package com.example.ems.service;

import com.example.ems.entity.Document;
import com.example.ems.entity.DocumentType;
import com.example.ems.entity.DocumentVersion;
import com.example.ems.entity.DocumentApprovalStatus;
import com.example.ems.entity.Employee;
import com.example.ems.entity.User;
import com.example.ems.repository.DocumentRepository;
import com.example.ems.repository.DocumentTypeRepository;
import com.example.ems.repository.DocumentVersionRepository;
import com.example.ems.repository.EmployeeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DocumentServiceTest {

    private static final byte[] CONTENT = "%PDF-1.7\nEmployee document".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path storageDirectory;

    private DocumentRepository documents;
    private DocumentTypeRepository types;
    private DocumentVersionRepository versions;
    private EmployeeRepository employees;
    private DocumentService service;

    @BeforeEach
    void setUp() {
        documents = mock(DocumentRepository.class);
        types = mock(DocumentTypeRepository.class);
        versions = mock(DocumentVersionRepository.class);
        employees = mock(EmployeeRepository.class);
        service = new DocumentService(documents, types, versions, employees);
        ReflectionTestUtils.setField(service, "storagePath", storageDirectory.toString());
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void removesFileWhenDatabaseRejectsMetadataAndTransactionRollsBack() throws IOException {
        prepareNewDocument();
        DataIntegrityViolationException databaseFailure = new DataIntegrityViolationException("metadata rejected");
        when(versions.save(any(DocumentVersion.class))).thenThrow(databaseFailure);
        TransactionSynchronizationManager.initSynchronization();

        assertSame(databaseFailure, assertThrows(DataIntegrityViolationException.class,
                () -> service.uploadDocument(7L, 2L, validFile(), "admin@example.com")));
        assertEquals(1, storedFiles().size());

        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertTrue(storedFiles().isEmpty());
    }

    @Test
    void removesFileWhenRollbackHappensAfterUploadMethodReturns() throws IOException {
        prepareNewDocument();
        TransactionSynchronizationManager.initSynchronization();

        service.uploadDocument(7L, 2L, validFile(), "admin@example.com");
        assertEquals(1, storedFiles().size());

        // A constraint or connection failure can surface only at transaction commit.
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertTrue(storedFiles().isEmpty());
    }

    @Test
    void keepsStoredFileWhenTransactionCommits() throws IOException {
        prepareNewDocument();
        TransactionSynchronizationManager.initSynchronization();

        service.uploadDocument(7L, 2L, validFile(), "admin@example.com");
        Path stored = storedFiles().getFirst();

        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        assertTrue(Files.exists(stored));
        assertArrayEquals(CONTENT, Files.readAllBytes(stored));
        assertFalse(stored.getFileName().toString().contains("employee.pdf"));
    }

    @Test
    void versionRollbackRemovesOnlyNewFileAndPreservesPreviousVersion() throws IOException {
        Path previousFile = storageDirectory.resolve("previous-version");
        Files.write(previousFile, CONTENT);
        Document existing = document();
        existing.setId(21L);
        existing.setCurrentVersion(1);
        when(documents.findByIdForUpdate(21L)).thenReturn(Optional.of(existing));
        when(documents.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();

        service.uploadNewVersion(21L, validFile(), "admin@example.com");
        assertEquals(2, storedFiles().size());

        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertEquals(List.of(previousFile), storedFiles());
        assertArrayEquals(CONTENT, Files.readAllBytes(previousFile));
    }

    @Test
    void removesPartialFileWhenInputStreamFails() throws IOException {
        prepareNewDocument();
        IOException streamFailure = new IOException("upload interrupted");
        MultipartFile interrupted = mock(MultipartFile.class);
        when(interrupted.getOriginalFilename()).thenReturn("employee.pdf");
        when(interrupted.getContentType()).thenReturn("application/pdf");
        when(interrupted.getSize()).thenReturn(100L);
        when(interrupted.getInputStream()).thenReturn(new InputStream() {
            private boolean firstRead = true;

            @Override
            public int read() throws IOException {
                throw streamFailure;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                if (firstRead) {
                    firstRead = false;
                    buffer[offset] = 'x';
                    return 1;
                }
                throw streamFailure;
            }
        });

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.uploadDocument(7L, 2L, interrupted, "admin@example.com"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failure.getStatusCode());
        assertSame(streamFailure, failure.getCause());
        assertTrue(storedFiles().isEmpty());
        verifyNoInteractions(versions);
    }

    @ParameterizedTest
    @MethodSource("invalidMetadata")
    void rejectsInvalidMetadataBeforeWritingFilesOrDatabase(String filename, String contentType)
            throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(filename);
        when(file.getContentType()).thenReturn(contentType);

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.uploadDocument(7L, 2L, file, "admin@example.com"));

        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
        assertTrue(storedFiles().isEmpty());
        verifyNoInteractions(documents, types, versions, employees);
    }

    static Stream<Arguments> invalidMetadata() {
        return Stream.of(
                Arguments.of(null, "application/pdf"),
                Arguments.of("", "application/pdf"),
                Arguments.of("   ", "application/pdf"),
                Arguments.of("x".repeat(256), "application/pdf"),
                Arguments.of("employee\r\n.pdf", "application/pdf"),
                Arguments.of("employee.pdf", null),
                Arguments.of("employee.pdf", "text/html"),
                Arguments.of("employee.pdf", "invalid mime type")
        );
    }

    @Test
    void rejectsOversizeUploadBeforeWritingFilesOrDatabase() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(10L * 1024 * 1024 + 1);

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.uploadDocument(7L, 2L, file, "admin@example.com"));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, failure.getStatusCode());
        assertTrue(storedFiles().isEmpty());
        verifyNoInteractions(documents, types, versions, employees);
    }

    @Test
    void approvalRecordsReviewerAndLatestApprovedVersion() {
        Document document = document();
        document.setId(21L);
        DocumentVersion version = version(document, 1, "user@example.com", DocumentApprovalStatus.PENDING);
        when(versions.findByDocumentIdAndVersionNumberForUpdate(21L, 1)).thenReturn(Optional.of(version));
        when(documents.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(versions.save(any(DocumentVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.reviewVersion(21L, 1, "admin@example.com", true, null);

        assertEquals(DocumentApprovalStatus.APPROVED, version.getStatus());
        assertEquals("admin@example.com", version.getApprovedBy());
        assertNotNull(version.getApprovedAt());
        assertEquals(1, document.getLatestApprovedVersion());
    }

    @Test
    void resubmittingRejectedDocumentCreatesNextPendingVersionAndKeepsHistory() {
        Document document = document();
        document.setId(21L);
        document.setCurrentVersion(1);
        document.setLatestApprovedVersion(null);
        User owner = new User(); owner.setId(5L); owner.setEmail("user@example.com");
        document.setOwner(owner);
        DocumentVersion rejected = version(document, 1, "user@example.com", DocumentApprovalStatus.REJECTED);
        rejected.setRejectionReason("Unreadable scan");
        when(documents.findByIdForUpdate(21L)).thenReturn(Optional.of(document));
        when(versions.findByDocumentIdAndVersionNumber(21L, 1)).thenReturn(Optional.of(rejected));
        when(documents.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(versions.save(any(DocumentVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();

        service.resubmit(21L, 5L, validFile(), "user@example.com");

        var saved = org.mockito.ArgumentCaptor.forClass(DocumentVersion.class);
        org.mockito.Mockito.verify(versions).save(saved.capture());
        assertEquals(2, saved.getValue().getVersionNumber());
        assertEquals(DocumentApprovalStatus.PENDING, saved.getValue().getStatus());
        assertEquals("Unreadable scan", rejected.getRejectionReason());
        assertEquals(2, document.getCurrentVersion());
    }

    private DocumentVersion version(Document document, int number, String uploadedBy, DocumentApprovalStatus status) {
        DocumentVersion version = new DocumentVersion();
        version.setDocument(document);
        version.setVersionNumber(number);
        version.setUploadedBy(uploadedBy);
        version.setStatus(status);
        return version;
    }

    private void prepareNewDocument() {
        Document document = document();
        when(employees.findById(7L)).thenReturn(Optional.of(document.getEmployee()));
        when(types.findById(2L)).thenReturn(Optional.of(document.getDocumentType()));
        when(documents.save(any(Document.class))).thenAnswer(invocation -> {
            Document saved = invocation.getArgument(0);
            saved.setId(21L);
            return saved;
        });
    }

    private Document document() {
        Employee employee = new Employee();
        employee.setId(7L);
        DocumentType type = new DocumentType();
        type.setId(2L);
        type.setName("Employment contract");
        Document document = new Document();
        document.setEmployee(employee);
        document.setDocumentType(type);
        return document;
    }

    private MultipartFile validFile() {
        return new MockMultipartFile("file", "employee.pdf", "application/pdf", CONTENT);
    }

    private List<Path> storedFiles() throws IOException {
        try (Stream<Path> files = Files.list(storageDirectory)) {
            return files.sorted().toList();
        }
    }

    private void completeTransaction(int status) {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(status));
        TransactionSynchronizationManager.clearSynchronization();
    }
}
