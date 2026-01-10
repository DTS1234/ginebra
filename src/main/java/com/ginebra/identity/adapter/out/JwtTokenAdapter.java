package com.ginebra.identity.adapter.out;

import com.ginebra.identity.domain.PlayerIdentity;
import com.ginebra.identity.port.out.TokenGenerator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

@Component
public class JwtTokenAdapter implements TokenGenerator {

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtTokenAdapter(JwtProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.secretKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
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
}
