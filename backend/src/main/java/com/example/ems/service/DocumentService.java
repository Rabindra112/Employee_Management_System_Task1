package com.example.ems.service;

import com.example.ems.dto.DocumentResponse;
import com.example.ems.dto.DocumentVersionResponse;
import com.example.ems.dto.PendingDocumentVersionResponse;

import com.example.ems.entity.Document;
import com.example.ems.entity.DocumentType;
import com.example.ems.entity.DocumentVersion;
import com.example.ems.entity.Employee;
import com.example.ems.entity.User;
import com.example.ems.entity.DocumentApprovalStatus;

import com.example.ems.exception.ResourceNotFoundException;

import com.example.ems.repository.DocumentRepository;
import com.example.ems.repository.DocumentTypeRepository;
import com.example.ems.repository.DocumentVersionRepository;
import com.example.ems.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;

    private final DocumentTypeRepository typeRepository;

    private final DocumentVersionRepository versionRepository;

    private final EmployeeRepository employeeRepository;

    @Value("${app.document.storage-path}")
    private String storagePath;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "image/jpeg",
            "image/png"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "jpg", "jpeg", "png");

    // =========================================
    // VALIDATE FILE
    // =========================================

    private void validateFile(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Please select a file"
            );
        }

        if (file.getSize() > 10 * 1024 * 1024) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "File size must not exceed 10 MB"
            );
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()
                || originalFilename.length() > 255
                || originalFilename.codePoints().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A file name of 1 to 255 characters without control characters is required"
            );
        }

        if (file.getContentType() == null || !ALLOWED_TYPES.contains(file.getContentType())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only PDF, DOCX, JPG and PNG are allowed"
            );
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT);
        String expectedType = switch (extension) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "jpg", "jpeg", "png" -> "image/" + (extension.equals("jpg") ? "jpeg" : extension);
            default -> "";
        };
        if (!ALLOWED_EXTENSIONS.contains(extension) || !expectedType.equals(file.getContentType())
                || !hasExpectedSignature(file, extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File content does not match an allowed file type");
        }
    }

    private boolean hasExpectedSignature(MultipartFile file, String extension) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(8);
            if (extension.equals("pdf")) return header.length >= 5 && header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F' && header[4] == '-';
            if (extension.equals("png")) return java.util.Arrays.equals(java.util.Arrays.copyOf(header, 8), new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
            if (extension.equals("jpg") || extension.equals("jpeg")) return header.length >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8 && (header[2] & 0xff) == 0xff;
            if (extension.equals("docx")) return header.length >= 4 && header[0] == 'P' && header[1] == 'K' && header[2] == 3 && header[3] == 4;
            return false;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to validate uploaded file", ex);
        }
    }

    // =========================================
    // SAVE FILE
    // =========================================

    private String saveFile(MultipartFile file) {

        Path directory = Paths.get(storagePath)
                .toAbsolutePath()
                .normalize();
        String fileName = UUID.randomUUID().toString();
        Path target = directory.resolve(fileName).normalize();

        if (!target.startsWith(directory)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path");
        }

        boolean fileCreated = false;
        try {
            Files.createDirectories(directory);

            try (InputStream input = file.getInputStream();
                 OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW,
                         StandardOpenOption.WRITE)) {
                fileCreated = true;
                input.transferTo(output);
            }

            // The file system does not participate in the database transaction.
            // Remove the stored file if inserting its metadata rolls back, including
            // failures that surface only when the surrounding transaction commits.
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            deleteStoredFile(target);
                        }
                    }
                });
            }

            return fileName;

        } catch (IOException ex) {
            if (fileCreated) {
                deleteStoredFile(target);
            }
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to store document",
                    ex
            );
        } catch (RuntimeException ex) {
            if (fileCreated) {
                deleteStoredFile(target);
            }
            throw ex;
        }
    }

    private void deleteStoredFile(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException | SecurityException ex) {
            // Keep the original transaction/storage exception as the failure.
            log.error("Unable to clean up document file {}", target, ex);
        }
    }

    // =========================================
    // UPLOAD NEW DOCUMENT
    // =========================================

    @Transactional
    public DocumentResponse uploadDocument(
            Long employeeId,
            Long documentTypeId,
            MultipartFile file,
            String uploadedBy) {

        validateFile(file);

        Employee employee = employeeRepository
                .findById(employeeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Employee not found"
                        )
                );

        DocumentType type = typeRepository
                .findById(documentTypeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Document type not found"
                        )
                );

        Document document = new Document();

        document.setEmployee(employee);
        document.setDocumentType(type);
        document.setDocumentName(file.getOriginalFilename());
        document.setCurrentVersion(1);

        document = documentRepository.save(document);

        String storedFile = saveFile(file);

        DocumentVersion version = new DocumentVersion();

        version.setDocument(document);
        version.setVersionNumber(1);
        version.setFileName(file.getOriginalFilename());
        version.setFilePath(storedFile);
        version.setContentType(file.getContentType());
        version.setFileSize(file.getSize());
        version.setUploadedBy(uploadedBy);

        versionRepository.save(version);

        return toResponse(document);
    }

    @Transactional
    public DocumentResponse uploadOwnDocument(User owner, Long documentTypeId, MultipartFile file) {
        validateFile(file);
        DocumentType type = typeRepository.findById(documentTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Document type not found"));
        Document document = new Document();
        document.setOwner(owner);
        document.setEmployee(owner.getEmployee());
        document.setDocumentType(type);
        document.setDocumentName(file.getOriginalFilename());
        document.setCurrentVersion(1);
        document = documentRepository.save(document);
        saveVersion(document, 1, file, owner.getEmail());
        return toResponse(document);
    }

    // =========================================
    // UPLOAD NEW VERSION
    // =========================================

    @Transactional
    public DocumentResponse uploadNewVersion(
            Long documentId,
            MultipartFile file,
            String uploadedBy) {

        validateFile(file);

        Document document = documentRepository
                .findByIdForUpdate(documentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Document not found"
                        )
                );

        int newVersion = document.getCurrentVersion() + 1;

        saveVersion(document, newVersion, file, uploadedBy);

        document.setCurrentVersion(newVersion);
        document.setDocumentName(file.getOriginalFilename());

        documentRepository.save(document);

        return toResponse(document);
    }

    private void saveVersion(Document document, int number, MultipartFile file, String uploadedBy) {
        DocumentVersion version = new DocumentVersion();
        version.setDocument(document);
        version.setVersionNumber(number);
        version.setFileName(file.getOriginalFilename());
        version.setFilePath(saveFile(file));
        version.setContentType(file.getContentType());
        version.setFileSize(file.getSize());
        version.setUploadedBy(uploadedBy);
        version.setStatus(DocumentApprovalStatus.PENDING);
        versionRepository.save(version);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> getOwnDocuments(Long ownerId) {
        return documentRepository.findByOwnerIdOrderByIdDesc(ownerId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PendingDocumentVersionResponse> getPendingVersions() {
        return versionRepository.findByStatusOrderByUploadedAtAsc(DocumentApprovalStatus.PENDING).stream()
                .map(v -> new PendingDocumentVersionResponse(v.getDocument().getId(), v.getVersionNumber(),
                        v.getFileName(), v.getDocument().getDocumentType().getName(),
                        v.getDocument().getEmployee() == null ? null : v.getDocument().getEmployee().getId(),
                        v.getUploadedBy(), v.getUploadedAt(), v.getStatus())).toList();
    }

    @Transactional
    public DocumentVersionResponse reviewVersion(Long documentId, Integer versionNumber, String reviewer, boolean approve, String reason) {
        DocumentVersion version = versionRepository.findByDocumentIdAndVersionNumberForUpdate(documentId, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Document version not found"));
        if (version.getStatus() != DocumentApprovalStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending document versions can be reviewed");
        }
        if (version.getUploadedBy().equalsIgnoreCase(reviewer)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrators cannot review their own uploads");
        }
        if (approve) {
            version.setStatus(DocumentApprovalStatus.APPROVED);
            version.setApprovedBy(reviewer);
            version.setApprovedAt(Instant.now());
            Document document = version.getDocument();
            if (document.getLatestApprovedVersion() == null || versionNumber > document.getLatestApprovedVersion()) {
                document.setLatestApprovedVersion(versionNumber);
                documentRepository.save(document);
            }
        } else {
            if (reason == null || reason.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A rejection reason is required");
            version.setStatus(DocumentApprovalStatus.REJECTED);
            version.setRejectedBy(reviewer);
            version.setRejectedAt(Instant.now());
            version.setRejectionReason(reason.trim());
        }
        return toVersionResponse(versionRepository.save(version));
    }

    @Transactional
    public DocumentResponse resubmit(Long documentId, Long ownerId, MultipartFile file, String uploader) {
        validateFile(file);
        Document document = documentRepository.findByIdForUpdate(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        if (document.getOwner() == null || !document.getOwner().getId().equals(ownerId)) {
            throw new org.springframework.security.access.AccessDeniedException("You can only access your own documents");
        }
        DocumentVersion latest = versionRepository.findByDocumentIdAndVersionNumber(documentId, document.getCurrentVersion())
                .orElseThrow(() -> new ResourceNotFoundException("Document version not found"));
        if (latest.getStatus() != DocumentApprovalStatus.REJECTED) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only rejected documents can be resubmitted");
        int next = document.getCurrentVersion() + 1;
        saveVersion(document, next, file, uploader);
        document.setCurrentVersion(next);
        document.setDocumentName(file.getOriginalFilename());
        return toResponse(documentRepository.save(document));
    }

    @Transactional(readOnly = true)
    public Long getDocumentOwnerId(Long documentId) {
        return documentRepository.findById(documentId).orElseThrow(() -> new ResourceNotFoundException("Document not found"))
                .getOwner() == null ? null : documentRepository.findById(documentId).orElseThrow().getOwner().getId();
    }

    @Transactional(readOnly = true)
    public List<DocumentVersionResponse> getOwnVersionHistory(Long documentId, Long ownerId) {
        if (getDocumentOwnerId(documentId) == null || !ownerId.equals(getDocumentOwnerId(documentId)))
            throw new org.springframework.security.access.AccessDeniedException("You can only access your own documents");
        return getVersionHistory(documentId);
    }

    // =========================================
    // GET EMPLOYEE DOCUMENTS
    // =========================================

    @Transactional(readOnly = true)
    public List<DocumentResponse> getEmployeeDocuments(
            Long employeeId) {

        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException(
                    "Employee not found"
            );
        }

        return documentRepository
                .findByEmployeeId(employeeId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // =========================================
    // GET DOCUMENT VERSION HISTORY
    // =========================================

    @Transactional(readOnly = true)
    public Long getDocumentEmployeeId(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"))
                .getEmployee().getId();
    }

    @Transactional(readOnly = true)
    public List<DocumentVersionResponse> getVersionHistory(
            Long documentId) {

        if (!documentRepository.existsById(documentId)) {
            throw new ResourceNotFoundException(
                    "Document not found"
            );
        }

        return versionRepository
                .findByDocumentIdOrderByVersionNumberDesc(
                        documentId
                )
                .stream()
                .map(this::toVersionResponse)
                .toList();
    }

    // =========================================
    // GET SPECIFIC VERSION
    // =========================================

    @Transactional(readOnly = true)
    public DocumentVersion getVersion(
            Long documentId,
            Integer versionNumber) {

        return versionRepository
                .findByDocumentIdAndVersionNumber(
                        documentId,
                        versionNumber
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Document version not found"
                        )
                );
    }

    @Transactional(readOnly = true)
    public DocumentVersion getOwnVersion(Long documentId, Integer versionNumber, Long ownerId) {
        return versionRepository.findByDocumentIdAndVersionNumberAndDocumentOwnerId(documentId, versionNumber, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Document version not found"));
    }

    // =========================================
    // DOWNLOAD DOCUMENT
    // =========================================

    public Resource downloadDocument(
            Long documentId,
            Integer versionNumber) {

        DocumentVersion version = getVersion(
                documentId,
                versionNumber
        );

        try {

            Path root = Paths.get(storagePath)
                    .toAbsolutePath()
                    .normalize();

            Path path = root
                    .resolve(version.getFilePath())
                    .normalize();

            if (!path.startsWith(root)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid file path"
                );
            }

            Resource resource = new UrlResource(
                    path.toUri()
            );

            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException(
                        "Stored file not found"
                );
            }

            return resource;

        } catch (java.net.MalformedURLException ex) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to read document"
            );
        }
    }

    // =========================================
    // CONVERT ENTITY TO DTO
    // =========================================

    private DocumentResponse toResponse(Document document) {
        DocumentVersion current = versionRepository.findByDocumentIdAndVersionNumber(document.getId(), document.getCurrentVersion()).orElse(null);
        return new DocumentResponse(
                document.getId(),
                document.getEmployee() == null ? null : document.getEmployee().getId(),
                document.getDocumentType().getName(),
                document.getDocumentName(),
                document.getCurrentVersion(),
                document.getCreatedAt(),
                current == null ? null : current.getStatus(),
                current == null ? null : current.getRejectionReason(),
                document.getLatestApprovedVersion(),
                document.getOwner() == null ? null : document.getOwner().getId()
        );
    }

    private DocumentVersionResponse toVersionResponse(DocumentVersion v) {
        return new DocumentVersionResponse(v.getId(), v.getDocument().getId(), v.getVersionNumber(), v.getFileName(),
                v.getContentType(), v.getFileSize(), v.getUploadedAt(), v.getUploadedBy(), v.getStatus(),
                v.getApprovedBy(), v.getApprovedAt(), v.getRejectedBy(), v.getRejectedAt(), v.getRejectionReason());
    }
}
