package com.example.ems.repository;

import com.example.ems.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @EntityGraph(attributePaths = "employee")
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmployeeId(Long employeeId);

    boolean existsByEmployeeIdAndIdNot(Long employeeId, Long id);

    long countByRole(String role);

    @EntityGraph(attributePaths = "employee")
    List<User> findAllByOrderByIdAsc();
}
