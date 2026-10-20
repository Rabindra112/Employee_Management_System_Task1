package com.example.ems.dto;

import com.example.ems.entity.DocumentApprovalStatus;
import java.time.Instant;

public record PendingDocumentVersionResponse(
        Long documentId,
        Integer versionNumber,
        String fileName,
        String documentType,
        Long employeeId,
        String uploadedBy,
        Instant uploadedAt,
        DocumentApprovalStatus status
) {}
