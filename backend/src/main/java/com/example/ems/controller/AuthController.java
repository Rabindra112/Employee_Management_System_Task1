package com.example.ems.controller;

import com.example.ems.dto.*;
import com.example.ems.entity.User;
import com.example.ems.repository.UserRepository;
import com.example.ems.security.RsaService;
import com.example.ems.security.UserRole;
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
import java.nio.charset.StandardCharsets;
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

    // RSA Service
    private final RsaService rsaService;

    @Value("${app.jwt.expiration-seconds:3600}")
    private long expiry;

    // Constructor Dependency Injection
    public AuthController(
            UserRepository r,
            PasswordEncoder e,
            AuthenticationManager a,
            JwtEncoder j,
            EmailService emailService,
            RsaService rsaService) {

        this.repo = r;
        this.encoder = e;
        this.manager = a;
        this.jwt = j;
        this.emailService = emailService;
        this.rsaService = rsaService;
    }

    // =====================================================
    // GET RSA PUBLIC KEY
    // =====================================================

    @GetMapping("/public-key")
    public Map<String, String> getPublicKey() {

        return Map.of(
                "publicKey",
                rsaService.getPublicKey()
        );
    }

    // =====================================================
    // SIGNUP WITH RSA + BCRYPT
    // =====================================================

    @PostMapping("/signup")
    public ResponseEntity<?> signup(
            @Valid @RequestBody SignupRequest req) {

        // Step 1: Decrypt password using RSA
        String decryptedPassword =
                decryptPassword(req.encryptedPassword());

        String decryptedConfirmPassword =
                decryptPassword(req.encryptedConfirmPassword());

        // Step 2: Validate password matching
        if (!decryptedPassword.equals(decryptedConfirmPassword)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Passwords do not match"
            );
        }

        // Step 3: Validate password length
        int passwordBytes = decryptedPassword
                .getBytes(StandardCharsets.UTF_8).length;

        if (passwordBytes < 8 || passwordBytes > 72) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must be between 8 and 72 UTF-8 bytes"
            );
        }

        // Step 4: Normalize email
        String email = req.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        // Step 5: Check duplicate email
        if (repo.existsByEmail(email)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email already registered"
            );
        }

        // Step 6: Create user
        User user = new User();

        user.setFullName(req.fullName().trim());
        user.setEmail(email);

        // Step 7: BCrypt password hashing
        user.setPassword(
                encoder.encode(decryptedPassword)
        );

        // Public registration never grants management permissions or claims an employee record.
        user.setRole("EMPLOYEE");

        // Step 8: Save user
        try {

            repo.saveAndFlush(user);

        } catch (DataIntegrityViolationException exception) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email already registered"
            );
        }

        log.info("New user registered successfully");

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of(
                        "message",
                        "Account created successfully. Please log in."
                ));
    }

    // =====================================================
    // LOGIN WITH RSA + SPRING SECURITY + JWT
    // =====================================================

    @PostMapping("/login")
    public Map<String, Object> login(
            @Valid @RequestBody LoginRequest req) {

        // Step 1: Normalize email
        String email = req.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        // Step 2: Decrypt RSA encrypted password
        String decryptedPassword =
                decryptPassword(req.encryptedPassword());

        // Step 3: Authenticate using Spring Security
        try {

            manager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            email,
                            decryptedPassword
                    )
            );

        } catch (AuthenticationException exception) {

            log.warn("Failed login attempt");

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password"
            );
        }

        // Step 4: Retrieve user
        User user = repo.findByEmail(email)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Invalid email or password"
                        )
                );

        if (UserRole.normalize(user.getRole()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account role is not supported");
        }

        // Step 5: Generate JWT
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("employee-management")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expiry))
                .subject(email)
                .claim("role", UserRole.normalize(user.getRole()))
                .build();

        String token = jwt.encode(
                JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        claims
                )
        ).getTokenValue();

        // Step 6: Send login email notification
        try {

            emailService.sendSuccessfulLoginEmail(
                    user.getEmail(),
                    user.getFullName(),
                    now
            );

        } catch (RuntimeException exception) {

            log.warn(
                    "Login alert email could not be scheduled ({}).",
                    exception.getClass().getSimpleName()
            );
        }

        log.info("User authenticated successfully");

        // Step 7: Return JWT and user details
        return Map.of(
                "accessToken", token,
                "tokenType", "Bearer",
                "expiresIn", expiry,
                "user", profile(user)
        );
    }

    // =====================================================
    // CURRENT LOGGED-IN USER
    // =====================================================

    @GetMapping("/me")
    public Map<String, Object> me(Authentication auth) {

        if (auth == null || !auth.isAuthenticated()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        User user = repo.findByEmail(auth.getName())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "User not found"
                        )
                );

        return profile(user);
    }

    // =====================================================
    // RSA PASSWORD DECRYPTION HELPER
    // =====================================================

    private String decryptPassword(String encryptedPassword) {

        try {

            return rsaService.decrypt(encryptedPassword);

        } catch (Exception exception) {

            log.warn("RSA password decryption failed");

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid encrypted password"
            );
        }
    }

    // =====================================================
    // USER PROFILE RESPONSE
    // =====================================================

    private Map<String, Object> profile(User user) {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("fullName", user.getFullName());
        result.put("email", user.getEmail());
        result.put("role", UserRole.normalize(user.getRole()));
        result.put("employeeId", user.getEmployee() == null ? null : user.getEmployee().getId());
        return result;
    }
}
