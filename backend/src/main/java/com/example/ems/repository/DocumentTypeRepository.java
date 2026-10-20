package com.example.ems.repository;

import com.example.ems.entity.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentTypeRepository
        extends JpaRepository<DocumentType, Long> {

    Optional<DocumentType> findByName(String name);

    java.util.List<DocumentType> findAllByOrderByIdAsc();
}
