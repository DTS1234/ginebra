package com.ginebra.identity.adapter.out;

import com.ginebra.identity.domain.PlayerId;
import com.ginebra.identity.domain.PlayerIdentity;
import com.ginebra.identity.port.out.TokenGenerator;
import com.ginebra.identity.port.out.TokenParser;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtTokenAdapter implements TokenGenerator, TokenParser {

    private final JwtProperties properties;
    private final SecretKey secretKey;
    private final JwtParser jwtParser;

    public JwtTokenAdapter(JwtProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.secretKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.jwtParser = Jwts.parser()
            .verifyWith(secretKey)
            .requireIssuer(properties.issuer())
            .build();
    }

    @Override
    public String generateToken(PlayerIdentity identity) {
        final var now = Instant.now();
        final var expiration = now.plus(properties.expirationDuration());

        return Jwts.builder()
            .subject(identity.playerId().toString())
            .claim("playerId", identity.playerId().toString())
            .claim("displayName", identity.displayName())
            .claim("anonymous", identity.anonymous())
            .issuer(properties.issuer())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(secretKey)
            .compact();
    }

    @Override
    public Optional<PlayerIdentity> parseToken(String token) {
        try {
            final var claims = jwtParser.parseSignedClaims(token).getPayload();

            final var playerId = new PlayerId(UUID.fromString(claims.get("playerId", String.class)));
            final var displayName = claims.get("displayName", String.class);
            final var anonymous = claims.get("anonymous", Boolean.class);

            return Optional.of(new PlayerIdentity(playerId, displayName, anonymous));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
