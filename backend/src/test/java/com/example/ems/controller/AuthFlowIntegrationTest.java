package com.example.ems.controller;

import com.example.ems.entity.Employee;
import com.example.ems.entity.User;
import com.example.ems.repository.EmployeeRepository;
import com.example.ems.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
        "app.jwt.secret=integration-test-secret-with-more-than-32-bytes",
        "spring.mail.username=sender@example.com",
        "app.mail.login-alert-enabled=true"
})
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    private static final String EMAIL = "riya@example.com";
    private static final String PASSWORD = "test-password";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private EmployeeRepository employeeRepository;
    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(user.getFullName()).thenReturn("Riya Sharma");
        when(user.getEmail()).thenReturn(EMAIL);
        when(user.getRole()).thenReturn("USER");
        when(user.getPassword()).thenReturn(passwordEncoder.encode(PASSWORD));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        doThrow(new MailSendException("Simulated SMTP outage"))
                .when(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void realJwtAndProtectedProfileWorkWhenSmtpFails() throws Exception {
        String token = logInAndAwaitNotification();
        Jwt jwt = jwtDecoder.decode(token);

        assertEquals(EMAIL, jwt.getSubject());
        assertEquals("USER", jwt.getClaimAsString("role"));
        assertEquals(3600, jwt.getExpiresAt().getEpochSecond() - jwt.getIssuedAt().getEpochSecond());

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.fullName").value("Riya Sharma"))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("USER"));

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertArrayEquals(new String[]{EMAIL}, message.getValue().getTo());
        assertTrue(message.getValue().getText().contains("Hello Riya Sharma,"));
        assertTrue(message.getValue().getText().contains(" IST"));
    }

    @Test
    void invalidCredentialsDoNotSendEmail() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"riya@example.com","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        verifyNoInteractions(mailSender, employeeRepository);
    }

    @Test
    void protectedRoutesRejectMissingAndInvalidBearerTokens() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/employees")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/employees/stats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(mailSender, employeeRepository);
    }

    @Test
    void employeeCrudSearchPaginationAndStatsRemainAvailableAfterSmtpFailure() throws Exception {
        String authorization = "Bearer " + logInAndAwaitNotification();
        Employee employee = new Employee(11L, "Alice", "IT", new BigDecimal("45000.00"));
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> {
            Employee saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(11L);
            }
            return saved;
        });

        mvc.perform(post("/api/employees").header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" Alice ","department":" IT ","salary":45000.00}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.department").value("IT"));

        mvc.perform(get("/api/employees/11").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Alice"));

        mvc.perform(put("/api/employees/11").header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alice Sharma","department":"HR","salary":55000.00}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Sharma"))
                .andExpect(jsonPath("$.department").value("HR"))
                .andExpect(jsonPath("$.salary").value(55000.00));

        when(employeeRepository.findByNameContainingIgnoreCaseAndDepartmentContainingIgnoreCase(
                eq("Alice"), eq("HR"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(employee), PageRequest.of(1, 2), 3));
        mvc.perform(get("/api/employees").header(HttpHeaders.AUTHORIZATION, authorization)
                        .param("name", " Alice ").param("department", " HR ")
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Alice Sharma"))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3));
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(employeeRepository).findByNameContainingIgnoreCaseAndDepartmentContainingIgnoreCase(
                eq("Alice"), eq("HR"), pageable.capture());
        assertEquals(Sort.by(Sort.Direction.DESC, "id"), pageable.getValue().getSort());

        when(employeeRepository.findAll()).thenReturn(List.of(employee));
        mvc.perform(get("/api/employees/stats").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEmployees").value(1))
                .andExpect(jsonPath("$.totalDepartments").value(1))
                .andExpect(jsonPath("$.totalSalary").value(55000.00));

        mvc.perform(delete("/api/employees/11").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNoContent());
        verify(employeeRepository).delete(employee);

        mvc.perform(get("/api/employees/999").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Employee not found with ID: 999"));
    }

    @Test
    void employeeAndLoginValidationRemainActive() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"invalid-email","password":""}
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(mailSender);

        String authorization = "Bearer " + logInAndAwaitNotification();
        mvc.perform(post("/api/employees").header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","department":"","salary":-1}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/employees").header(HttpHeaders.AUTHORIZATION, authorization)
                        .param("page", "-1").param("size", "0"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(employeeRepository);
    }

    private String logInAndAwaitNotification() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"Riya@Example.com","password":"test-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andReturn();
        verify(mailSender, timeout(3000)).send(any(SimpleMailMessage.class));
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("accessToken").asText();
    }
}
