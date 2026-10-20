package com.example.ems.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "employee_id",
            nullable = true
    )
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "document_type_id",
            nullable = false
    )
    private DocumentType documentType;

    @Column(nullable = false)
    private String documentName;

    @Column(nullable = false)
    private Integer currentVersion = 1;

    @Column(name = "latest_approved_version")
    private Integer latestApprovedVersion;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
