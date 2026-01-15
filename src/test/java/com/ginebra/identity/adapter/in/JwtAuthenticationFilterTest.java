package com.ginebra.identity.adapter.in;

import com.ginebra.identity.domain.PlayerId;
import com.ginebra.identity.domain.PlayerIdentity;
import com.ginebra.identity.port.out.TokenParser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    private TestTokenParser tokenParser;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        tokenParser = new TestTokenParser();
        filter = new JwtAuthenticationFilter(tokenParser);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPopulateSecurityContextWithValidToken() throws ServletException, IOException {
        // Arrange
        final var playerId = new PlayerId(UUID.randomUUID());
        final var playerIdentity = new PlayerIdentity(playerId, "TestPlayer", true);
        final var token = "valid.jwt.token";

        tokenParser.addValidToken(token, playerIdentity);

        final var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        final var response = new MockHttpServletResponse();
        final var filterChain = new MockFilterChain();

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        final var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isInstanceOf(PlayerAuthentication.class);

        final var playerAuth = (PlayerAuthentication) authentication;
        assertThat(playerAuth.getPlayerIdentity()).isEqualTo(playerIdentity);
        assertThat(playerAuth.isAuthenticated()).isTrue();
    }

    @Test
    void shouldClearSecurityContextWithInvalidToken() throws ServletException, IOException {
        // Arrange
        final var token = "invalid.jwt.token";

        final var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        final var response = new MockHttpServletResponse();
        final var filterChain = new MockFilterChain();

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        final var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();
    }

    @Test
    void shouldNotPopulateSecurityContextWhenNoAuthHeader() throws ServletException, IOException {
        // Arrange
        final var request = new MockHttpServletRequest();
        final var response = new MockHttpServletResponse();
        final var filterChain = new MockFilterChain();

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        final var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();
        assertThat(tokenParser.parseAttempts).isEmpty();
    }

    @Test
    void shouldIgnoreMalformedAuthorizationHeader() throws ServletException, IOException {
        // Arrange
        final var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "InvalidFormat token");
        final var response = new MockHttpServletResponse();
        final var filterChain = new MockFilterChain();

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        final var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();
        assertThat(tokenParser.parseAttempts).isEmpty();
    }

    @Test
    void shouldIgnoreAuthorizationHeaderWithoutBearer() throws ServletException, IOException {
        // Arrange
        final var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        final var response = new MockHttpServletResponse();
        final var filterChain = new MockFilterChain();

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        final var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();
        assertThat(tokenParser.parseAttempts).isEmpty();
    }

    @Test
    void shouldExtractTokenCorrectly() throws ServletException, IOException {
        // Arrange
        final var token = "my.jwt.token.with.dots";

        final var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        final var response = new MockHttpServletResponse();
        final var filterChain = new MockFilterChain();

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        assertThat(tokenParser.parseAttempts).containsExactly(token);
    }

    // Test double - in-memory TokenParser
    private static class TestTokenParser implements TokenParser {

        private final Map<String, PlayerIdentity> validTokens = new HashMap<>();
        private final List<String> parseAttempts = new ArrayList<>();

        void addValidToken(String token, PlayerIdentity identity) {
            validTokens.put(token, identity);
        }

        @Override
        public Optional<PlayerIdentity> parseToken(String token) {
            parseAttempts.add(token);
            return Optional.ofNullable(validTokens.get(token));
        }
    }
}
