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
}
