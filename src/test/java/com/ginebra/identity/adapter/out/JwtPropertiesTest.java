package com.ginebra.identity.adapter.out;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtPropertiesTest {

    @Test
    void shouldCreatePropertiesWithAllValues() {
        // Act
        final var properties = new JwtProperties(
            "this-is-a-secret-key-with-more-than-32-characters",
            48,
            "test-issuer"
        );

        // Assert
        assertThat(properties.secret()).isEqualTo("this-is-a-secret-key-with-more-than-32-characters");
        assertThat(properties.expirationHours()).isEqualTo(48);
        assertThat(properties.issuer()).isEqualTo("test-issuer");
    }

    @Test
    void shouldRejectNullSecret() {
        // Act & Assert
        assertThatThrownBy(() -> new JwtProperties(null, 24, "issuer"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("secret must be at least 32 characters");
    }

    @Test
    void shouldRejectShortSecret() {
        // Act & Assert
        assertThatThrownBy(() -> new JwtProperties("short", 24, "issuer"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("secret must be at least 32 characters");
    }

    @Test
    void shouldUseDefaultExpirationWhenNull() {
        // Act
        final var properties = new JwtProperties(
            "this-is-a-secret-key-with-more-than-32-characters",
            null,
            "issuer"
        );

        // Assert
        assertThat(properties.expirationHours()).isEqualTo(24);
    }

    @Test
    void shouldUseDefaultExpirationWhenZero() {
        // Act
        final var properties = new JwtProperties(
            "this-is-a-secret-key-with-more-than-32-characters",
            0,
            "issuer"
        );

        // Assert
        assertThat(properties.expirationHours()).isEqualTo(24);
    }

    @Test
    void shouldUseDefaultExpirationWhenNegative() {
        // Act
        final var properties = new JwtProperties(
            "this-is-a-secret-key-with-more-than-32-characters",
            -1,
            "issuer"
        );

        // Assert
        assertThat(properties.expirationHours()).isEqualTo(24);
    }

    @Test
    void shouldUseDefaultIssuerWhenNull() {
        // Act
        final var properties = new JwtProperties(
            "this-is-a-secret-key-with-more-than-32-characters",
            24,
            null
        );

        // Assert
        assertThat(properties.issuer()).isEqualTo("ginebra-online");
    }

    @Test
    void shouldUseDefaultIssuerWhenBlank() {
        // Act
        final var properties = new JwtProperties(
            "this-is-a-secret-key-with-more-than-32-characters",
            24,
            "  "
        );

        // Assert
        assertThat(properties.issuer()).isEqualTo("ginebra-online");
    }

    @Test
    void shouldConvertExpirationToDuration() {
        // Arrange
        final var properties = new JwtProperties(
            "this-is-a-secret-key-with-more-than-32-characters",
            48,
            "issuer"
        );

        // Act
        final var duration = properties.expirationDuration();

        // Assert
        assertThat(duration).isEqualTo(Duration.ofHours(48));
    }
}
