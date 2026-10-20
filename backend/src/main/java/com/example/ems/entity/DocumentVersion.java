package com.example.ems.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import com.example.ems.entity.DocumentApprovalStatus;

@Entity
@Table(
        name = "document_versions",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {
                        "document_id",
                        "version_number"
                }
        )
)
@Getter
@Setter
@NoArgsConstructor
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "document_id",
            nullable = false
    )
    private Document document;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false, unique = true)
    private String filePath;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false)
    private Instant uploadedAt = Instant.now();

    @Column(nullable = false)
    private String uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentApprovalStatus status = DocumentApprovalStatus.PENDING;

    private String approvedBy;
    private Instant approvedAt;
    private String rejectedBy;
    private Instant rejectedAt;

    @Column(length = 1000)
    private String rejectionReason;
}
