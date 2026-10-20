package com.example.ems.repository;

import com.example.ems.entity.RoleManagementLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RoleManagementLockRepository extends JpaRepository<RoleManagementLock, Long> {
    @Query(value = "SELECT * FROM role_management_lock WHERE id = 1 LIMIT 1", nativeQuery = true)
    Optional<RoleManagementLock> lockForRoleChange();
}
