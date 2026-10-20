package com.example.ems.dto;

import java.time.Instant;
import com.example.ems.entity.DocumentApprovalStatus;

public record DocumentResponse(

        Long id,

        Long employeeId,

        String documentType,

        String documentName,

        Integer currentVersion,

        Instant createdAt,
        DocumentApprovalStatus status,
        String rejectionReason,
        Integer latestApprovedVersion,
        Long ownerUserId

) {}
