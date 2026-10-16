package com.example.ems.controller;

import com.example.ems.dto.*;
import com.example.ems.entity.User;
import com.example.ems.repository.UserRepository;
import com.example.ems.service.EmailService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository repo;
    private final PasswordEncoder encoder;
    private final AuthenticationManager manager;
    private final JwtEncoder jwt;
    private final EmailService emailService;

    @Value("${app.jwt.expiration-seconds:3600}")
    private long expiry;

    public AuthController(
            UserRepository r,
            PasswordEncoder e,
            AuthenticationManager a,
            JwtEncoder j,
            EmailService emailService) {

        repo = r;
        encoder = e;
        manager = a;
        jwt = j;
        this.emailService = emailService;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest req) {

        if (!req.password().equals(req.confirmPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Passwords do not match"
            );
        }

        String email = req.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (repo.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email already registered"
            );
        }

        User u = new User();

        u.setFullName(req.fullName().trim());
        u.setEmail(email);
        u.setPassword(encoder.encode(req.password()));

        try {
            repo.saveAndFlush(u);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email already registered"
            );
        }

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of(
                        "message",
                        "Account created. Please log in."
                ));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest req) {

        String email = req.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        try {
            manager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            email,
                            req.password()
                    )
            );
        } catch (AuthenticationException e) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password"
            );
        }

        User user = repo.findByEmail(email).orElseThrow();

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("employee-management")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expiry))
                .subject(email)
                .claim("role", user.getRole())
                .build();

        String token = jwt.encode(
                JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        claims
                )
        ).getTokenValue();

        try {
            emailService.sendSuccessfulLoginEmail(
                    user.getEmail(),
                    user.getFullName(),
                    now
            );
        } catch (RuntimeException exception) {
            // A full or shutting-down executor must not turn a successful login into an error.
            log.warn(
                    "Login alert email could not be scheduled ({}).",
                    exception.getClass().getSimpleName()
            );
        }

        return Map.of(
                "accessToken", token,
                "tokenType", "Bearer",
                "expiresIn", expiry,
                "user", profile(user)
        );
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication auth) {

        User user = repo.findByEmail(auth.getName())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED
                        )
                );

        return profile(user);
    }

    private Map<String, Object> profile(User u) {

        return Map.of(
                "id", u.getId(),
                "fullName", u.getFullName(),
                "email", u.getEmail(),
                "role", u.getRole()
        );
    }
}