package com.ideamanagement.platform.security;

import com.nimbusds.jwt.JWTParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${clerk.mock:true}")
    private boolean mockEnabled;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    private final TenantContextFilter tenantContextFilter;

    public SecurityConfig(TenantContextFilter tenantContextFilter) {
        this.tenantContextFilter = tenantContextFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Bearer tokens are stateless and immune to CSRF
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Allow public access to Swagger UI, API docs, and invite code check
                .requestMatchers("/api/organizations/invite/**").permitAll()
                .requestMatchers("/api/mock-jwks/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder())))
            .addFilterAfter(tenantContextFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("http://localhost*", "http://127.0.0.1*", "http://[::1]*", frontendUrl));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        if (mockEnabled) {
            return new JwtDecoder() {
                @Override
                public Jwt decode(String token) throws JwtException {
                    try {
                        String[] parts = token.split("\\.");
                        if (parts.length < 2) {
                            throw new JwtException("Invalid token format");
                        }
                        byte[] payloadBytes;
                        try {
                            payloadBytes = java.util.Base64.getUrlDecoder().decode(parts[1]);
                        } catch (Exception e) {
                            payloadBytes = java.util.Base64.getDecoder().decode(parts[1]);
                        }
                        
                        @SuppressWarnings("unchecked")
                        Map<String, Object> claims = new com.fasterxml.jackson.databind.ObjectMapper().readValue(payloadBytes, Map.class);
                        
                        Object expClaim = claims.get("exp");
                        Object iatClaim = claims.get("iat");
                        
                        Instant iat = iatClaim != null ? Instant.ofEpochSecond(((Number) iatClaim).longValue()) : Instant.now();
                        Instant exp = expClaim != null ? Instant.ofEpochSecond(((Number) expClaim).longValue()) : Instant.now().plusSeconds(3600);

                        return new Jwt(
                                token,
                                iat,
                                exp,
                                Map.of("alg", "none"),
                                claims
                        );
                    } catch (Exception e) {
                        throw new JwtException("Failed to decode mock JWT token", e);
                    }
                }
            };
        }
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }
}
