package com.ginebra.identity.adapter.out;

import com.ginebra.identity.domain.PlayerId;
import com.ginebra.identity.domain.PlayerIdentity;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenAdapterTest {

    private JwtTokenAdapter tokenAdapter;
    private JwtProperties properties;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties(
            "test-secret-key-minimum-32-characters-for-testing-purposes",
            1,
            "test-issuer"
        );
        tokenAdapter = new JwtTokenAdapter(properties);
    }

    @Test
    void shouldGenerateValidJwtToken() {
        // Arrange
        final var playerId = PlayerId.generate();
        final var identity = PlayerIdentity.createAnonymous(playerId, Optional.of("TestPlayer"));

        // Act
        final var token = tokenAdapter.generateToken(identity);

        // Assert
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts: header.payload.signature
    }

    @Test
    void shouldIncludeAllClaimsInToken() {
        // Arrange
        final var playerId = PlayerId.generate();
        final var identity = PlayerIdentity.createAnonymous(playerId, Optional.of("TestPlayer"));

        // Act
        final var token = tokenAdapter.generateToken(identity);

        // Verify by parsing the token
        final var secretKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        final var claims = Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();

        // Assert
        assertThat(claims.getSubject()).isEqualTo(playerId.toString());
        assertThat(claims.get("playerId", String.class)).isEqualTo(playerId.toString());
        assertThat(claims.get("displayName", String.class)).isEqualTo("TestPlayer");
        assertThat(claims.get("anonymous", Boolean.class)).isTrue();
        assertThat(claims.getIssuer()).isEqualTo("test-issuer");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
    }

    @Test
    void shouldGenerateDifferentTokensForDifferentIdentities() {
        // Arrange
        final var identity1 = PlayerIdentity.createAnonymous(PlayerId.generate(), Optional.of("Player1"));
        final var identity2 = PlayerIdentity.createAnonymous(PlayerId.generate(), Optional.of("Player2"));

        // Act
        final var token1 = tokenAdapter.generateToken(identity1);
        final var token2 = tokenAdapter.generateToken(identity2);

        // Assert
        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    void shouldIncludeExpirationInToken() {
        // Arrange
        final var playerId = PlayerId.generate();
        final var identity = PlayerIdentity.createAnonymous(playerId, Optional.of("TestPlayer"));

        // Act
        final var token = tokenAdapter.generateToken(identity);

        // Parse token to verify expiration
        final var secretKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        final var claims = Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();

        // Assert
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void shouldParseValidToken() {
        // Arrange
        final var playerId = PlayerId.generate();
        final var originalIdentity = PlayerIdentity.createAnonymous(playerId, Optional.of("TestPlayer"));
        final var token = tokenAdapter.generateToken(originalIdentity);

        // Act
        final var parsedIdentity = tokenAdapter.parseToken(token);

        // Assert
        assertThat(parsedIdentity).isPresent();
        assertThat(parsedIdentity.get().playerId()).isEqualTo(originalIdentity.playerId());
        assertThat(parsedIdentity.get().displayName()).isEqualTo("TestPlayer");
        assertThat(parsedIdentity.get().anonymous()).isTrue();
    }

    @Test
    void shouldReturnEmptyForInvalidToken() {
        // Arrange
        final var invalidToken = "invalid.token.here";

        // Act
        final var parsedIdentity = tokenAdapter.parseToken(invalidToken);

        // Assert
        assertThat(parsedIdentity).isEmpty();
    }

    @Test
    void shouldReturnEmptyForExpiredToken() {
        // Arrange - Manually create an expired token
        final var playerId = PlayerId.generate();
        final var secretKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));

        final var now = java.time.Instant.now();
        final var pastExpiration = now.minus(java.time.Duration.ofHours(1));

        final var expiredToken = Jwts.builder()
            .subject(playerId.toString())
            .claim("playerId", playerId.toString())
            .claim("displayName", "TestPlayer")
            .claim("anonymous", true)
            .issuer(properties.issuer())
            .issuedAt(java.util.Date.from(now.minus(java.time.Duration.ofHours(2))))
            .expiration(java.util.Date.from(pastExpiration))
            .signWith(secretKey)
            .compact();

        // Act
        final var parsedIdentity = tokenAdapter.parseToken(expiredToken);

        // Assert
        assertThat(parsedIdentity).isEmpty();
    }

    @Test
    void shouldReturnEmptyForWrongIssuer() {
        // Arrange - Create token with different issuer
        final var wrongIssuerProperties = new JwtProperties(
            "test-secret-key-minimum-32-characters-for-testing-purposes",
            1,
            "wrong-issuer"
        );
        final var wrongIssuerAdapter = new JwtTokenAdapter(wrongIssuerProperties);
        final var playerId = PlayerId.generate();
        final var identity = PlayerIdentity.createAnonymous(playerId, Optional.of("TestPlayer"));
        final var wrongIssuerToken = wrongIssuerAdapter.generateToken(identity);

        // Act
        final var parsedIdentity = tokenAdapter.parseToken(wrongIssuerToken);

        // Assert
        assertThat(parsedIdentity).isEmpty();
    }

    @Test
    void shouldReturnEmptyForMalformedToken() {
        // Arrange
        final var malformedToken = "not-a-jwt-token";

        // Act
        final var parsedIdentity = tokenAdapter.parseToken(malformedToken);

        // Assert
        assertThat(parsedIdentity).isEmpty();
    }
}
