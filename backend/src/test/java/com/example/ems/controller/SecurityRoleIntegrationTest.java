package com.example.ems.controller;

import com.example.ems.entity.Document;
import com.example.ems.entity.DocumentType;
import com.example.ems.entity.DocumentVersion;
import com.example.ems.entity.Employee;
import com.example.ems.entity.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecurityRoleIntegrationTest extends AuthenticatedIntegrationTestSupport {

    @Test
    void publicSignupCannotCreateAdminOrLinkAnEmployee() throws Exception {
        String encrypted = encryptedPassword(PASSWORD);
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "New Employee", "email", "new@example.com",
                                "encryptedPassword", encrypted, "encryptedConfirmPassword", encrypted,
                                "role", "ADMIN", "employeeId", 11))))
                .andExpect(status().isCreated());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertEquals("EMPLOYEE", saved.getValue().getRole());
        assertNull(saved.getValue().getEmployee());
        assertTrue(passwordEncoder.matches(PASSWORD, saved.getValue().getPassword()));
    }

    @Test
    void employeeSeesOnlyLinkedProfileAndLegacyUserRoleRemainsCompatible() throws Exception {
        Employee employee = employee(11L, "Riya Sharma");
        useEmployeeAccount("USER", employee);
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(employee));
        String authorization = authorization("USER");

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.employeeId").value(11));
        mvc.perform(get("/api/employees/me").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.name").value("Riya Sharma"));
    }

    @Test
    void unlinkedEmployeeHasAccountProfileAndReceivesNotFoundForEmployeeProfile() throws Exception {
        useEmployeeAccount("EMPLOYEE", null);
        String authorization = authorization("EMPLOYEE");
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").isEmpty());
        mvc.perform(get("/api/employees/me").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNotFound());
    }

    @Test
    void employeeCannotAccessAdminEmployeeManagementOrAccountRoutes() throws Exception {
        useEmployeeAccount("EMPLOYEE", employee(11L, "Riya Sharma"));
        String authorization = authorization("EMPLOYEE");
        for (String path : List.of("/api/employees", "/api/employees/stats", "/api/employees/11",
                "/api/employees/12", "/api/admin/accounts")) {
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, authorization))
                    .andExpect(status().isForbidden());
        }
        String body = "{\"name\":\"Edited\",\"department\":\"IT\",\"salary\":100}";
        mvc.perform(post("/api/employees").header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/employees/11").header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/employees/11").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/accounts/7/employee").header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"employeeId\":11}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(employeeRepository);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void databaseRoleOverridesForgedOrStaleAdminTokenClaim() throws Exception {
        useEmployeeAccount("EMPLOYEE", employee(11L, "Riya Sharma"));
        mvc.perform(get("/api/employees").header(HttpHeaders.AUTHORIZATION, authorization("ADMIN")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(employeeRepository);
    }

    @Test
    void currentAdminRoleIsAppliedEvenWhenTokenContainsOldEmployeeRole() throws Exception {
        when(userRepository.findAllByOrderByIdAsc()).thenReturn(List.of(currentUser));
        mvc.perform(get("/api/admin/accounts").header(HttpHeaders.AUTHORIZATION, authorization("EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("ADMIN"));
    }

    @Test
    void deletedAccountCannotReuseExistingToken() throws Exception {
        String authorization = authorization("ADMIN");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/employees").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(employeeRepository);
    }

    @Test
    void unsupportedDatabaseRoleCannotAuthenticateUsingAdminClaim() throws Exception {
        currentUser.setRole("SUPER_ADMIN");
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, authorization("ADMIN")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(employeeRepository);
    }

    @Test
    void anonymousUsersCannotReadOrModifyDocuments() throws Exception {
        for (String path : List.of("/api/documents/types", "/api/documents/employee/11",
                "/api/documents/21/versions", "/api/documents/21/versions/1/download")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/documents/types").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ID\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(documentRepository, documentTypeRepository, documentVersionRepository);
    }

    @Test
    void employeeCannotReadAnotherEmployeesDocumentsHistoryOrDownload() throws Exception {
        useEmployeeAccount("EMPLOYEE", employee(11L, "Riya Sharma"));
        Document otherDocument = document(22L, employee(12L, "Another Employee"));
        when(documentRepository.findById(22L)).thenReturn(Optional.of(otherDocument));
        String authorization = authorization("EMPLOYEE");

        for (String path : List.of("/api/documents/employee/12", "/api/documents/22/versions",
                "/api/documents/22/versions/1/download")) {
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, authorization))
                    .andExpect(status().isForbidden());
        }
        verify(documentRepository, never()).findByEmployeeId(12L);
        verifyNoInteractions(documentVersionRepository);
    }

    @Test
    void unlinkedEmployeeCannotReadAnyEmployeesDocuments() throws Exception {
        useEmployeeAccount("EMPLOYEE", null);
        mvc.perform(get("/api/documents/employee/11")
                        .header(HttpHeaders.AUTHORIZATION, authorization("EMPLOYEE")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(documentVersionRepository);
    }

    @Test
    void employeeCanReadOwnDocumentsHistoryAndDownload() throws Exception {
        Employee employee = employee(11L, "Riya Sharma");
        useEmployeeAccount("EMPLOYEE", employee);
        Document document = document(21L, employee);
        when(employeeRepository.existsById(11L)).thenReturn(true);
        when(documentRepository.findById(21L)).thenReturn(Optional.of(document));
        when(documentRepository.existsById(21L)).thenReturn(true);
        when(documentRepository.findByEmployeeId(11L)).thenReturn(List.of(document));
        DocumentVersion version = version(document);
        when(documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(21L)).thenReturn(List.of(version));
        when(documentVersionRepository.findByDocumentIdAndVersionNumber(21L, 1)).thenReturn(Optional.of(version));
        String authorization = authorization("EMPLOYEE");

        mvc.perform(get("/api/documents/employee/11").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeId").value(11));
        mvc.perform(get("/api/documents/21/versions").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].versionNumber").value(1));
        mvc.perform(get("/api/documents/21/versions/1/download").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().bytes("sample-document".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void employeeCanReadDocumentTypesButCannotCreateOrUpload() throws Exception {
        useEmployeeAccount("EMPLOYEE", employee(11L, "Riya Sharma"));
        when(documentTypeRepository.findAll()).thenReturn(List.of());
        String authorization = authorization("EMPLOYEE");
        mvc.perform(get("/api/documents/types").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk());
        mvc.perform(post("/api/documents/types").header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ID\"}"))
                .andExpect(status().isForbidden());
        MockMultipartFile file = new MockMultipartFile("file", "id.pdf", "application/pdf", new byte[]{1});
        mvc.perform(multipart("/api/documents/upload").file(file)
                        .param("employeeId", "11").param("documentTypeId", "1")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/documents/21/versions").file(file)
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isForbidden());
        verify(documentTypeRepository, never()).save(any(DocumentType.class));
        verifyNoInteractions(documentRepository, documentVersionRepository);
    }

    @Test
    void employeeCanSeeOnlyDocumentsFromAuthenticatedAccountAndCannotReview() throws Exception {
        useEmployeeAccount("USER", null);
        when(documentRepository.findByOwnerIdOrderByIdDesc(7L)).thenReturn(List.of());
        String authorization = authorization("EMPLOYEE");

        mvc.perform(get("/api/documents/my").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/documents/21/versions/1/approve").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isForbidden());

        verify(documentRepository).findByOwnerIdOrderByIdDesc(7L);
        verify(documentVersionRepository, never()).findByDocumentIdAndVersionNumber(any(), any());
    }

    @Test
    void employeeUploadUsesAuthenticatedAccountInsteadOfSubmittedEmployeeId() throws Exception {
        useEmployeeAccount("EMPLOYEE", null);
        DocumentType type = new DocumentType(); type.setId(2L); type.setName("ID");
        when(documentTypeRepository.findById(2L)).thenReturn(Optional.of(type));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document saved = invocation.getArgument(0); saved.setId(21L); return saved;
        });
        when(documentVersionRepository.save(any(DocumentVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MockMultipartFile file = new MockMultipartFile("file", "id.pdf", "application/pdf",
                "%PDF-1.7\nfile".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/documents/my/upload").file(file).param("documentTypeId", "2")
                        .param("employeeId", "999").header(HttpHeaders.AUTHORIZATION, authorization("EMPLOYEE")))
                .andExpect(status().isCreated());

        ArgumentCaptor<Document> created = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(created.capture());
        assertEquals(7L, created.getValue().getOwner().getId());
        assertNull(created.getValue().getEmployee());
        assertEquals(1, created.getValue().getCurrentVersion());
    }

    @Test
    void adminCanApprovePendingVersionAndDuplicateReviewIsRejected() throws Exception {
        Document document = document(21L, employee(11L, "Riya Sharma"));
        DocumentVersion version = version(document);
        when(documentVersionRepository.findByDocumentIdAndVersionNumberForUpdate(21L, 1)).thenReturn(Optional.of(version));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentVersionRepository.save(any(DocumentVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String authorization = authorization("ADMIN");

        mvc.perform(patch("/api/documents/21/versions/1/approve").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(patch("/api/documents/21/versions/1/approve").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCanReadPendingQueueAndRejectWithRequiredReason() throws Exception {
        Document document = document(29L, employee(11L, "Riya Sharma"));
        DocumentVersion version = version(document);
        when(documentVersionRepository.findByStatusOrderByUploadedAtAsc(com.example.ems.entity.DocumentApprovalStatus.PENDING))
                .thenReturn(List.of(version));
        when(documentVersionRepository.findByDocumentIdAndVersionNumberForUpdate(29L, 1)).thenReturn(Optional.of(version));
        when(documentVersionRepository.save(any(DocumentVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String authorization = authorization("ADMIN");

        mvc.perform(get("/api/documents/pending").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].uploadedBy").value("admin@example.com"));
        mvc.perform(patch("/api/documents/29/versions/1/reject").header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Unreadable scan\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Unreadable scan"));
    }

    @Test
    void adminCanReadDocumentsForAnyEmployeeAndCreateTypes() throws Exception {
        Employee employee = employee(12L, "Another Employee");
        Document document = document(22L, employee);
        when(employeeRepository.existsById(12L)).thenReturn(true);
        when(documentRepository.findByEmployeeId(12L)).thenReturn(List.of(document));
        when(documentRepository.findById(22L)).thenReturn(Optional.of(document));
        when(documentRepository.existsById(22L)).thenReturn(true);
        when(documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(22L)).thenReturn(List.of());
        when(documentTypeRepository.save(any(DocumentType.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String authorization = authorization("ADMIN");
        mvc.perform(get("/api/documents/employee/12").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeId").value(12));
        mvc.perform(get("/api/documents/22/versions").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk());
        mvc.perform(post("/api/documents/types").header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" ID \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("ID"));
    }

    @Test
    void adminAccountListingOmitsPasswordsAndIncludesEmployeeLink() throws Exception {
        User account = employeeAccount(8L);
        account.setEmployee(employee(11L, "Riya Sharma"));
        when(userRepository.findAllByOrderByIdAsc()).thenReturn(List.of(currentUser, account));
        mvc.perform(get("/api/admin/accounts").header(HttpHeaders.AUTHORIZATION, authorization("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[1].employeeId").value(11))
                .andExpect(jsonPath("$[1].password").doesNotExist());
    }

    @Test
    void adminCanLinkAndUnlinkEmployeeAccounts() throws Exception {
        User account = employeeAccount(8L);
        when(userRepository.findById(8L)).thenReturn(Optional.of(account));
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(employee(11L, "Employee")));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String authorization = authorization("ADMIN");

        mvc.perform(put("/api/admin/accounts/8/employee").header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"employeeId\":11}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(11));
        assertEquals(11L, account.getEmployee().getId());

        mvc.perform(put("/api/admin/accounts/8/employee").header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"employeeId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").isEmpty());
        assertNull(account.getEmployee());
    }

    @Test
    void linkingFailsForEmployeeAlreadyAssignedToAnotherAccount() throws Exception {
        User account = employeeAccount(8L);
        when(userRepository.findById(8L)).thenReturn(Optional.of(account));
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(employee(11L, "Employee")));
        when(userRepository.existsByEmployeeIdAndIdNot(11L, 8L)).thenReturn(true);

        mvc.perform(put("/api/admin/accounts/8/employee")
                        .header(HttpHeaders.AUTHORIZATION, authorization("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"employeeId\":11}"))
                .andExpect(status().isConflict());
        assertNull(account.getEmployee());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void adminAccountsCannotBeLinkedAndMissingEmployeesReturnNotFound() throws Exception {
        when(userRepository.findById(7L)).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(8L)).thenReturn(Optional.of(employeeAccount(8L)));
        String authorization = authorization("ADMIN");
        mvc.perform(put("/api/admin/accounts/7/employee").header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"employeeId\":11}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/accounts/8/employee").header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"employeeId\":999}"))
                .andExpect(status().isNotFound());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void deletingEmployeeWithLinkedAccountReturnsConflict() throws Exception {
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(employee(11L, "Employee")));
        when(userRepository.existsByEmployeeId(11L)).thenReturn(true);
        mvc.perform(delete("/api/employees/11").header(HttpHeaders.AUTHORIZATION, authorization("ADMIN")))
                .andExpect(status().isConflict());
        verify(employeeRepository, never()).delete(any(Employee.class));
    }

    private User employeeAccount(Long id) {
        User account = new User();
        account.setId(id);
        account.setEmail("employee@example.com");
        account.setFullName("Employee Account");
        account.setPassword("must-never-be-returned");
        account.setRole("EMPLOYEE");
        return account;
    }

    private Employee employee(Long id, String name) {
        return new Employee(id, name, "IT", new BigDecimal("45000.00"));
    }

    private Document document(Long id, Employee employee) {
        DocumentType type = new DocumentType();
        type.setId(1L);
        type.setName("ID");
        Document document = new Document();
        document.setId(id);
        document.setEmployee(employee);
        document.setDocumentType(type);
        document.setDocumentName("id.pdf");
        return document;
    }

    private DocumentVersion version(Document document) throws Exception {
        Path path = Files.createTempFile(DOCUMENT_STORAGE, "own-document-", ".pdf");
        Files.writeString(path, "sample-document");
        path.toFile().deleteOnExit();
        DocumentVersion version = new DocumentVersion();
        version.setId(31L);
        version.setDocument(document);
        version.setVersionNumber(1);
        version.setFileName("id.pdf");
        version.setFilePath(path.getFileName().toString());
        version.setContentType("application/pdf");
        version.setFileSize(Files.size(path));
        version.setUploadedBy("admin@example.com");
        version.setStatus(com.example.ems.entity.DocumentApprovalStatus.PENDING);
        return version;
    }
}
