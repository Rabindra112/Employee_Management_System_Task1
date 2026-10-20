package com.example.ems.controller;

import com.example.ems.dto.LoginRequest;
import com.example.ems.entity.User;
import com.example.ems.repository.UserRepository;
import com.example.ems.security.RsaService;
import com.example.ems.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private static final String NORMALIZED_LOGIN_EMAIL = "login@example.com";
    private static final String REGISTERED_EMAIL = "registered@example.com";
    private static final String FULL_NAME = "Riya Sharma";
    private static final String ACCESS_TOKEN = "test-access-token";

    private UserRepository repository;
    private AuthenticationManager authenticationManager;
    private JwtEncoder jwtEncoder;
    private EmailService emailService;
    private AuthController controller;

    @BeforeEach
    void setUp() throws Exception {
        repository = mock(UserRepository.class);
        authenticationManager = mock(AuthenticationManager.class);
        jwtEncoder = mock(JwtEncoder.class);
        emailService = mock(EmailService.class);
        RsaService rsaService = mock(RsaService.class);
        when(rsaService.decrypt("encrypted-password")).thenReturn("test-password");
        controller = new AuthController(repository, mock(PasswordEncoder.class),
                authenticationManager, jwtEncoder, emailService, rsaService);
        ReflectionTestUtils.setField(controller, "expiry", 3600L);
    }

    @Test
    void successfulLoginNotifiesRegisteredUserAfterAuthenticationAndJwtCreation() {
        stubRegisteredUser();
        stubEncodedToken();

        Map<String, Object> response = controller.login(loginRequest());

        ArgumentCaptor<Authentication> authentication = ArgumentCaptor.forClass(Authentication.class);
        ArgumentCaptor<JwtEncoderParameters> parameters = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        ArgumentCaptor<Instant> loginTime = ArgumentCaptor.forClass(Instant.class);
        InOrder order = inOrder(authenticationManager, jwtEncoder, emailService);
        order.verify(authenticationManager).authenticate(authentication.capture());
        order.verify(jwtEncoder).encode(parameters.capture());
        order.verify(emailService).sendSuccessfulLoginEmail(
                eq(REGISTERED_EMAIL), eq(FULL_NAME), loginTime.capture());

        assertEquals(NORMALIZED_LOGIN_EMAIL, authentication.getValue().getName());
        assertEquals("test-password", authentication.getValue().getCredentials());
        assertEquals("employee-management", parameters.getValue().getClaims().getClaimAsString("iss"));
        assertEquals(NORMALIZED_LOGIN_EMAIL, parameters.getValue().getClaims().getSubject());
        assertEquals("EMPLOYEE", parameters.getValue().getClaims().getClaimAsString("role"));
        assertEquals(parameters.getValue().getClaims().getIssuedAt(), loginTime.getValue());
        assertEquals(loginTime.getValue().plusSeconds(3600),
                parameters.getValue().getClaims().getExpiresAt());
        assertSuccessfulResponse(response);
    }

    @Test
    void invalidCredentialsDoNotGenerateJwtOrSendEmail() {
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.login(loginRequest()));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("Invalid email or password", exception.getReason());
        verifyNoInteractions(repository, jwtEncoder, emailService);
    }

    @Test
    void jwtEncodingFailureDoesNotSendEmail() {
        stubRegisteredUser();
        when(jwtEncoder.encode(any(JwtEncoderParameters.class)))
                .thenThrow(new JwtEncodingException("JWT encoding unavailable"));

        assertThrows(JwtEncodingException.class, () -> controller.login(loginRequest()));

        verify(authenticationManager).authenticate(any(Authentication.class));
        verifyNoInteractions(emailService);
    }

    @Test
    void fullEmailQueueDoesNotPreventSuccessfulLogin() {
        assertLoginSurvivesNotificationFailure(new TaskRejectedException("Email queue is full"));
    }

    @Test
    void notificationSubmissionFailureDoesNotPreventSuccessfulLogin() {
        assertLoginSurvivesNotificationFailure(new IllegalStateException("Email service unavailable"));
    }

    private void assertLoginSurvivesNotificationFailure(RuntimeException failure) {
        stubRegisteredUser();
        stubEncodedToken();
        doThrow(failure).when(emailService)
                .sendSuccessfulLoginEmail(eq(REGISTERED_EMAIL), eq(FULL_NAME), any(Instant.class));

        Map<String, Object> response = controller.login(loginRequest());

        assertSuccessfulResponse(response);
        verify(emailService).sendSuccessfulLoginEmail(
                eq(REGISTERED_EMAIL), eq(FULL_NAME), any(Instant.class));
    }

    private void stubRegisteredUser() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(user.getEmail()).thenReturn(REGISTERED_EMAIL);
        when(user.getFullName()).thenReturn(FULL_NAME);
        when(user.getRole()).thenReturn("USER");
        when(repository.findByEmail(NORMALIZED_LOGIN_EMAIL)).thenReturn(Optional.of(user));
    }

    private void stubEncodedToken() {
        Jwt token = Jwt.withTokenValue(ACCESS_TOKEN)
                .header("alg", "HS256")
                .subject(NORMALIZED_LOGIN_EMAIL)
                .build();
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(token);
    }

    private LoginRequest loginRequest() {
        return new LoginRequest("  Login@Example.com  ", "encrypted-password");
    }

    private void assertSuccessfulResponse(Map<String, Object> response) {
        assertEquals(ACCESS_TOKEN, response.get("accessToken"));
        assertEquals("Bearer", response.get("tokenType"));
        assertEquals(3600L, response.get("expiresIn"));
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) response.get("user");
        assertEquals(7L, profile.get("id"));
        assertEquals(FULL_NAME, profile.get("fullName"));
        assertEquals(REGISTERED_EMAIL, profile.get("email"));
        assertEquals("EMPLOYEE", profile.get("role"));
        assertNull(profile.get("employeeId"));
    }
}
