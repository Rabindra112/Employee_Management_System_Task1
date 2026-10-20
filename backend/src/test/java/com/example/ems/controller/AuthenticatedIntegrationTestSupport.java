package com.example.ems.controller;

import com.example.ems.entity.Employee;
import com.example.ems.entity.User;
import com.example.ems.repository.DocumentRepository;
import com.example.ems.repository.DocumentTypeRepository;
import com.example.ems.repository.DocumentVersionRepository;
import com.example.ems.repository.EmployeeRepository;
import com.example.ems.repository.RoleManagementLockRepository;
import com.example.ems.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.MGF1ParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
        "app.jwt.secret=integration-test-secret-with-more-than-32-bytes",
        "spring.mail.username=sender@example.com",
        "app.mail.login-alert-enabled=true"
})
@AutoConfigureMockMvc
abstract class AuthenticatedIntegrationTestSupport {

    protected static final String EMAIL = "riya@example.com";
    protected static final String PASSWORD = "test-password";
    private static final KeyPair RSA_KEYS;
    private static final Path PRIVATE_KEY_PATH;
    private static final Path PUBLIC_KEY_PATH;
    protected static final Path DOCUMENT_STORAGE;

    static {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            RSA_KEYS = generator.generateKeyPair();
            Path directory = Files.createTempDirectory("ems-role-integration-");
            PRIVATE_KEY_PATH = directory.resolve("private.pem");
            PUBLIC_KEY_PATH = directory.resolve("public.pem");
            DOCUMENT_STORAGE = directory.resolve("documents");
            Files.createDirectory(DOCUMENT_STORAGE);
            Files.writeString(PRIVATE_KEY_PATH, pem("PRIVATE KEY", RSA_KEYS.getPrivate().getEncoded()));
            Files.writeString(PUBLIC_KEY_PATH, pem("PUBLIC KEY", RSA_KEYS.getPublic().getEncoded()));
            directory.toFile().deleteOnExit();
            DOCUMENT_STORAGE.toFile().deleteOnExit();
            PRIVATE_KEY_PATH.toFile().deleteOnExit();
            PUBLIC_KEY_PATH.toFile().deleteOnExit();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry properties) {
        properties.add("app.rsa.private-key-path", PRIVATE_KEY_PATH::toString);
        properties.add("app.rsa.public-key-path", PUBLIC_KEY_PATH::toString);
        properties.add("app.document.storage-path", DOCUMENT_STORAGE::toString);
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected JwtDecoder jwtDecoder;
    @Autowired protected JwtEncoder jwtEncoder;

    @MockitoBean protected UserRepository userRepository;
    @MockitoBean protected RoleManagementLockRepository roleManagementLockRepository;
    @MockitoBean protected EmployeeRepository employeeRepository;
    @MockitoBean protected DocumentRepository documentRepository;
    @MockitoBean protected DocumentTypeRepository documentTypeRepository;
    @MockitoBean protected DocumentVersionRepository documentVersionRepository;
    @MockitoBean protected JavaMailSender mailSender;

    protected User currentUser;

    @BeforeEach
    void setUpAuthenticatedAccount() {
        currentUser = new User();
        currentUser.setId(7L);
        currentUser.setFullName("Riya Sharma");
        currentUser.setEmail(EMAIL);
        currentUser.setRole("ADMIN");
        currentUser.setPassword(passwordEncoder.encode(PASSWORD));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(currentUser));
        doThrow(new MailSendException("Simulated SMTP outage"))
                .when(mailSender).send(any(SimpleMailMessage.class));
    }

    protected void useEmployeeAccount(String storedRole, Employee employee) {
        currentUser.setRole(storedRole);
        currentUser.setEmployee(employee);
    }

    protected String authorization(String claimedRole) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("employee-management")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .subject(EMAIL)
                .claim("role", claimedRole)
                .build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    protected String encryptedPassword(String password) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        cipher.init(Cipher.ENCRYPT_MODE, RSA_KEYS.getPublic(), new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
        return Base64.getEncoder().encodeToString(cipher.doFinal(password.getBytes(StandardCharsets.UTF_8)));
    }

    private static String pem(String label, byte[] bytes) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(bytes)
                + "\n-----END " + label + "-----\n";
    }
}
