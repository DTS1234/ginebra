package com.ginebra.identity.adapter.in;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Objects;

/**
 * Minimal Spring Security configuration for JWT-based authentication.
 * Disables default Spring Security features and configures custom JWT filter.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = Objects.requireNonNull(
            jwtAuthenticationFilter,
            "jwtAuthenticationFilter must not be null"
        );
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable features not needed for stateless JWT authentication
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)

            // Stateless session (no server-side session)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints - no authentication required
                .requestMatchers(HttpMethod.POST, "/api/auth/anonymous").permitAll()

                // Static play client - the page itself is public, every call it makes is not
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/app.js", "/style.css", "/favicon.svg", "/favicon.ico")
                .permitAll()

                // Protected endpoints - authentication required
                .requestMatchers("/api/auth/me").authenticated()
                .requestMatchers("/api/rooms/**").authenticated()
                .requestMatchers("/api/games/**").authenticated()

                // WebSocket endpoints - HTTP upgrade is permitted, auth enforced at STOMP level
                .requestMatchers("/ws/**").permitAll()

                // All other requests require authentication by default
                .anyRequest().authenticated()
            )

            // Configure authentication entry point to return 401 instead of 403
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                })
            )

            // Register our custom JWT filter before Spring Security's authentication filter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
