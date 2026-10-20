package com.example.ems.repository;

import com.example.ems.entity.DocumentVersion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import com.example.ems.entity.DocumentApprovalStatus;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface DocumentVersionRepository
        extends JpaRepository<DocumentVersion, Long> {

    List<DocumentVersion>
    findByDocumentIdOrderByVersionNumberDesc(Long documentId);

    Optional<DocumentVersion>
    findByDocumentIdAndVersionNumber(
            Long documentId,
            Integer versionNumber
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT v FROM DocumentVersion v WHERE v.document.id = :documentId AND v.versionNumber = :versionNumber")
    Optional<DocumentVersion> findByDocumentIdAndVersionNumberForUpdate(
            @org.springframework.data.repository.query.Param("documentId") Long documentId,
            @org.springframework.data.repository.query.Param("versionNumber") Integer versionNumber);

    List<DocumentVersion> findByStatusOrderByUploadedAtAsc(DocumentApprovalStatus status);

    Optional<DocumentVersion> findByDocumentIdAndVersionNumberAndDocumentOwnerId(
            Long documentId, Integer versionNumber, Long ownerId);
}
