package com.ginebra.identity.adapter.in;

import com.ginebra.identity.domain.PlayerIdentity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * Custom Authentication implementation that wraps PlayerIdentity.
 * Used to store authenticated player in Spring SecurityContext.
 */
public final class PlayerAuthentication implements Authentication {

    private final PlayerIdentity playerIdentity;
    private final String token;
    private final boolean authenticated;

    private PlayerAuthentication(PlayerIdentity playerIdentity, String token, boolean authenticated) {
        this.playerIdentity = Objects.requireNonNull(playerIdentity, "playerIdentity must not be null");
        this.token = Objects.requireNonNull(token, "token must not be null");
        this.authenticated = authenticated;
    }

    /**
     * Creates an authenticated PlayerAuthentication instance.
     */
    public static PlayerAuthentication authenticated(PlayerIdentity playerIdentity, String token) {
        return new PlayerAuthentication(playerIdentity, token, true);
    }

    /**
     * Returns the PlayerIdentity for this authentication.
     */
    public PlayerIdentity getPlayerIdentity() {
        return playerIdentity;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // No role-based security needed for this game
        return Collections.emptyList();
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getDetails() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return playerIdentity;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        if (isAuthenticated) {
            throw new IllegalArgumentException(
                "Cannot set authenticated to true - use authenticated() factory method"
            );
        }
    }

    @Override
    public String getName() {
        return playerIdentity.playerId().value().toString();
    }
}
