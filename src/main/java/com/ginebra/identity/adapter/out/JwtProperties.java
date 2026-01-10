package com.ginebra.identity.adapter.out;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ginebra.jwt")
public record JwtProperties(
    String secret,
    Integer expirationHours,
    String issuer
) {
    public JwtProperties {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("secret must be at least 32 characters");
        }

        if (expirationHours == null || expirationHours <= 0) {
            expirationHours = 24;
        }

        if (issuer == null || issuer.isBlank()) {
            issuer = "ginebra-online";
        }
    }

    public Duration expirationDuration() {
        return Duration.ofHours(expirationHours);
    }
}
