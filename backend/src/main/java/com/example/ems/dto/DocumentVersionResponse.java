package com.example.ems.dto;

import java.time.Instant;
import com.example.ems.entity.DocumentApprovalStatus;

public record DocumentVersionResponse(

        Long id,

        Long documentId,

        Integer versionNumber,

        String fileName,

        String contentType,

        Long fileSize,

        Instant uploadedAt,

        String uploadedBy,
        DocumentApprovalStatus status,
        String approvedBy,
        Instant approvedAt,
        String rejectedBy,
        Instant rejectedAt,
        String rejectionReason

) {}
