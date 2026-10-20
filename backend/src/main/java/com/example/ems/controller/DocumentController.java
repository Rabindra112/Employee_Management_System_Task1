package com.example.ems.controller;

import com.example.ems.dto.DocumentResponse;
import com.example.ems.dto.DocumentVersionResponse;
import com.example.ems.dto.DocumentRejectionRequest;

import com.example.ems.entity.DocumentType;
import com.example.ems.entity.DocumentVersion;

import com.example.ems.repository.DocumentTypeRepository;
import com.example.ems.service.DocumentService;
import com.example.ems.service.CurrentUserService;

import lombok.RequiredArgsConstructor;

import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    private final DocumentTypeRepository typeRepository;
    private final CurrentUserService currentUsers;

    // =========================================
    // CREATE DOCUMENT TYPE
    // =========================================

    @PostMapping("/types")
    public ResponseEntity<DocumentType> createDocumentType(
            @RequestBody DocumentType type) {

        if (type.getName() == null ||
                type.getName().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Document type name is required"
            );
        }

        String name = type.getName().trim().toUpperCase(Locale.ROOT);
        if (name.length() > 100 || (type.getDescription() != null && type.getDescription().length() > 255)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document type fields are too long");
        }

        if (typeRepository.findByName(name).isPresent()) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Document type already exists"
            );
        }

        DocumentType newType = new DocumentType();

        newType.setName(name);
        newType.setDescription(type.getDescription());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(typeRepository.save(newType));
    }

    // =========================================
    // GET DOCUMENT TYPES
    // =========================================

    @GetMapping("/types")
    public List<DocumentType> getDocumentTypes() {

        return typeRepository.findAllByOrderByIdAsc();
    }

    @GetMapping("/my")
    public List<DocumentResponse> getMyDocuments(Authentication authentication) {
        return documentService.getOwnDocuments(currentUsers.requireAccountId(authentication));
    }

    @PostMapping(value = "/my/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadMyDocument(@RequestParam Long documentTypeId,
            @RequestParam MultipartFile file, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentService.uploadOwnDocument(
                currentUsers.requireDocumentOwner(authentication), documentTypeId, file));
    }

    @GetMapping("/pending")
    public List<com.example.ems.dto.PendingDocumentVersionResponse> getPendingDocuments() {
        return documentService.getPendingVersions();
    }

    @PatchMapping("/{documentId}/versions/{versionNumber}/approve")
    public DocumentVersionResponse approveVersion(@PathVariable Long documentId, @PathVariable Integer versionNumber,
            Authentication authentication) {
        return documentService.reviewVersion(documentId, versionNumber, authentication.getName(), true, null);
    }

    @PatchMapping("/{documentId}/versions/{versionNumber}/reject")
    public DocumentVersionResponse rejectVersion(@PathVariable Long documentId, @PathVariable Integer versionNumber,
            @Valid @RequestBody DocumentRejectionRequest request, Authentication authentication) {
        return documentService.reviewVersion(documentId, versionNumber, authentication.getName(), false, request.reason());
    }

    @PostMapping(value = "/{documentId}/resubmit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentResponse resubmit(@PathVariable Long documentId, @RequestParam MultipartFile file, Authentication authentication) {
        return documentService.resubmit(documentId, currentUsers.requireAccountId(authentication), file, authentication.getName());
    }

    // =========================================
    // UPLOAD NEW DOCUMENT
    // =========================================

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<DocumentResponse> uploadDocument(

            @RequestParam Long employeeId,

            @RequestParam Long documentTypeId,

            @RequestParam MultipartFile file,

            Authentication authentication) {

        DocumentResponse response =
                documentService.uploadDocument(
                        employeeId,
                        documentTypeId,
                        file,
                        authentication.getName()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // =========================================
    // UPLOAD NEW VERSION
    // =========================================

    @PostMapping(
            value = "/{documentId}/versions",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public DocumentResponse uploadNewVersion(

            @PathVariable Long documentId,

            @RequestParam MultipartFile file,

            Authentication authentication) {

        return documentService.uploadNewVersion(
                documentId,
                file,
                authentication.getName()
        );
    }

    // =========================================
    // GET EMPLOYEE DOCUMENTS
    // =========================================

    @GetMapping("/employee/{employeeId}")
    public List<DocumentResponse> getEmployeeDocuments(

            @PathVariable Long employeeId, Authentication authentication) {

        currentUsers.requireEmployeeAccess(authentication, employeeId);

        return documentService.getEmployeeDocuments(
                employeeId
        );
    }

    // =========================================
    // GET VERSION HISTORY
    // =========================================

    @GetMapping("/{documentId}/versions")
    public List<DocumentVersionResponse> getVersionHistory(

            @PathVariable Long documentId, Authentication authentication) {

        if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            // Admins may review all history.
        } else if (documentService.getDocumentOwnerId(documentId) != null) {
            documentService.getOwnVersionHistory(documentId, currentUsers.requireAccountId(authentication));
        } else {
            currentUsers.requireEmployeeAccess(authentication, documentService.getDocumentEmployeeId(documentId));
        }

        return documentService.getVersionHistory(
                documentId
        );
    }

    // =========================================
    // DOWNLOAD SPECIFIC VERSION
    // =========================================

    @GetMapping(
            "/{documentId}/versions/{versionNumber}/download"
    )
    public ResponseEntity<Resource> downloadVersion(

            @PathVariable Long documentId,

            @PathVariable Integer versionNumber, Authentication authentication) {

        DocumentVersion version;
        if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            version = documentService.getVersion(documentId, versionNumber);
        } else if (documentService.getDocumentOwnerId(documentId) != null) {
            version = documentService.getOwnVersion(documentId, versionNumber, currentUsers.requireAccountId(authentication));
            if (version.getStatus() != com.example.ems.entity.DocumentApprovalStatus.APPROVED) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only approved versions can be downloaded");
            }
        } else {
            currentUsers.requireEmployeeAccess(authentication, documentService.getDocumentEmployeeId(documentId));
            version = documentService.getVersion(documentId, versionNumber);
        }
        /*DocumentVersion version = documentService.getVersion(
                documentId,
                versionNumber
        );*/

        Resource resource = documentService.downloadDocument(
                documentId,
                versionNumber
        );

        ContentDisposition disposition = ContentDisposition
                .attachment()
                .filename(
                        version.getFileName(),
                        StandardCharsets.UTF_8
                )
                .build();

        return ResponseEntity.ok()
                .contentType(
                        MediaType.APPLICATION_OCTET_STREAM
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition.toString()
                )
                .body(resource);
    }
}
