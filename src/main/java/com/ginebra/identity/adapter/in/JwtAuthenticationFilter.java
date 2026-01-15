package com.ginebra.identity.adapter.in;

import com.ginebra.identity.port.out.TokenParser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/**
 * Filter that extracts and validates JWT tokens from Authorization header.
 * Populates SecurityContext with authenticated PlayerIdentity if token is valid.
 * Allows requests without tokens to proceed (authorization decisions happen elsewhere).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenParser tokenParser;

    public JwtAuthenticationFilter(TokenParser tokenParser) {
        this.tokenParser = Objects.requireNonNull(tokenParser, "tokenParser must not be null");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        // Extract token from header
        final var authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            final var token = authHeader.substring(BEARER_PREFIX.length());

            // Validate and parse token
            tokenParser.parseToken(token).ifPresentOrElse(
                playerIdentity -> {
                    // Valid token - populate SecurityContext
                    final var authentication = PlayerAuthentication.authenticated(playerIdentity, token);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("Authenticated player: {} ({})",
                        playerIdentity.displayName(),
                        playerIdentity.playerId());
                },
                () -> {
                    // Invalid token - log but continue (endpoint will handle 401)
                    log.debug("Invalid or expired token");
                    SecurityContextHolder.clearContext();
                }
            );
        } else {
            // No token present - clear context
            SecurityContextHolder.clearContext();
        }

        // Always continue filter chain - let endpoints decide if auth is required
        filterChain.doFilter(request, response);
    }
}
