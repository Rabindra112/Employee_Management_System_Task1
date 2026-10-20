package com.example.ems.config;

import com.example.ems.repository.UserRepository;
import com.example.ems.security.UserRole;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository repo) {
        return email -> repo.findByEmail(email)
                .map(u -> User.withUsername(u.getEmail())
                        .password(u.getPassword())
                        .roles(UserRole.normalize(u.getRole()))
                        .build())
                .orElseThrow(() ->
                        new UsernameNotFoundException("Invalid credentials"));
    }

    @Bean
    AuthenticationManager authenticationManager(
            UserDetailsService uds,
            PasswordEncoder encoder) {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(uds);

        provider.setPasswordEncoder(encoder);

        return new ProviderManager(provider);
    }

    @Bean
    JwtEncoder jwtEncoder(
            @Value("${app.jwt.secret}") String secret) {

        return new NimbusJwtEncoder(
                new ImmutableSecret<>(
                        secret.getBytes(StandardCharsets.UTF_8)
                )
        );
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${app.jwt.secret}") String secret) {

        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "JWT_SECRET must be at least 32 bytes"
            );
        }

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(
                        new SecretKeySpec(
                                secret.getBytes(StandardCharsets.UTF_8),
                                "HmacSHA256"
                        )
                )
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("employee-management"));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter accountJwtAuthenticationConverter(UserRepository users) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(token -> {
            if (token.getSubject() == null || token.getSubject().isBlank()) {
                throw new InvalidBearerTokenException("Missing account subject");
            }
            var user = users.findByEmail(token.getSubject())
                    .orElseThrow(() -> new InvalidBearerTokenException("Account no longer exists"));
            String role = UserRole.normalize(user.getRole());
            if (role.isEmpty()) throw new InvalidBearerTokenException("Account role is not supported");
            // Use current database permissions, so role changes apply to existing tokens too.
            return List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.frontend-origin}") String origin) {

        CorsConfiguration c = new CorsConfiguration();

        c.setAllowedOrigins(List.of(origin));

        c.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        );

        c.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "Origin", "Accept")
        );

        UrlBasedCorsConfigurationSource src =
                new UrlBasedCorsConfigurationSource();

        src.registerCorsConfiguration("/**", c);

        return src;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    JwtAuthenticationConverter accountJwtAuthenticationConverter) throws Exception {

        return http
                .csrf(csrf -> csrf.disable())

                .cors(cors -> {
                })

                .sessionManagement(s ->
                        s.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth

                        // Allow login and signup
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/login",
                                "/api/auth/signup"
                        )
                        .permitAll()

                        // Allow RSA public key retrieval
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/auth/public-key"
                        )
                        .permitAll()

                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/employees/me").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers("/api/employees", "/api/employees/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/documents/my").hasRole("EMPLOYEE")
                        .requestMatchers(HttpMethod.POST, "/api/documents/my/**").hasRole("EMPLOYEE")
                        .requestMatchers(HttpMethod.POST, "/api/documents/*/resubmit").hasRole("EMPLOYEE")
                        .requestMatchers("/api/documents/pending", "/api/documents/*/versions/*/approve",
                                "/api/documents/*/versions/*/reject").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/documents/**").hasAnyRole("ADMIN", "EMPLOYEE")
                        .requestMatchers("/api/documents/**").hasRole("ADMIN")

                        // All other endpoints require authentication
                        .anyRequest()
                        .authenticated()
                )

                .oauth2ResourceServer(o -> o.jwt(j ->
                        j.jwtAuthenticationConverter(accountJwtAuthenticationConverter)))

                .build();
    }
}
