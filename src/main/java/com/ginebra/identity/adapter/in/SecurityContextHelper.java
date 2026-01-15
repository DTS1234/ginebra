package com.ginebra.identity.adapter.in;

import com.ginebra.identity.domain.PlayerIdentity;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Utility for accessing the authenticated PlayerIdentity from SecurityContext.
 * Provides type-safe access without casting.
 */
public final class SecurityContextHelper {

    private SecurityContextHelper() {
        // Utility class - no instances
    }

    /**
     * Returns the authenticated PlayerIdentity from current SecurityContext.
     *
     * @return Optional containing PlayerIdentity if authenticated, empty otherwise
     */
    public static Optional<PlayerIdentity> getCurrentPlayerIdentity() {
        final var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof PlayerAuthentication playerAuth) {
            return Optional.of(playerAuth.getPlayerIdentity());
        }

        return Optional.empty();
    }

    /**
     * Returns the authenticated PlayerIdentity, throwing exception if not present.
     *
     * @return PlayerIdentity
     * @throws IllegalStateException if no authenticated player in context
     */
    public static PlayerIdentity requireCurrentPlayerIdentity() {
        return getCurrentPlayerIdentity()
            .orElseThrow(() -> new IllegalStateException(
                "No authenticated player in SecurityContext"
            ));
    }
}
