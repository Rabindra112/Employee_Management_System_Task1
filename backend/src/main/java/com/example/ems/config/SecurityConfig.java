package com.example.ems.config;

import com.example.ems.repository.UserRepository;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
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
                        .roles(u.getRole())
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

        return NimbusJwtDecoder
                .withSecretKey(
                        new SecretKeySpec(
                                secret.getBytes(StandardCharsets.UTF_8),
                                "HmacSHA256"
                        )
                )
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.frontend-origin}") String origin) {

        CorsConfiguration c = new CorsConfiguration();

        c.setAllowedOrigins(List.of(origin));

        c.setAllowedMethods(
                List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")
        );

        c.setAllowedHeaders(
                List.of("Authorization", "Content-Type")
        );

        UrlBasedCorsConfigurationSource src =
                new UrlBasedCorsConfigurationSource();

        src.registerCorsConfiguration("/**", c);

        return src;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        return http
                .csrf(csrf -> csrf.disable())

                .cors(cors -> {
                })

                .sessionManagement(s ->
                        s.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(a -> a
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/login",
                                "/api/auth/signup"
                        )
                        .permitAll()

                        .anyRequest()
                        .authenticated()
                )

                .oauth2ResourceServer(o ->
                        o.jwt(j -> {
                        })
                )

                .build();
    }
}