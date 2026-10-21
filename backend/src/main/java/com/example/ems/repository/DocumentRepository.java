package com.example.ems.repository;

import com.example.ems.entity.Document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import com.example.ems.entity.Document;

public interface DocumentRepository
                extends JpaRepository<Document, Long> {

        List<Document> findByEmployeeId(Long employeeId);

        List<Document> findByOwnerIdOrderByIdDesc(Long ownerId);

        boolean existsByEmployeeId(Long employeeId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @org.springframework.data.jpa.repository.Query("SELECT d FROM Document d WHERE d.id = :id")
        Optional<Document> findByIdForUpdate(
                        @org.springframework.data.repository.query.Param("id") Long id);
}
