package com.example.ems.service;

import com.example.ems.dto.UserAccountResponse;
import com.example.ems.entity.User;
import com.example.ems.repository.RoleManagementLockRepository;
import com.example.ems.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserRoleService {
    private final UserRepository userRepository;
    private final RoleManagementLockRepository roleManagementLockRepository;

    @Transactional(readOnly = true)
    public List<UserAccountResponse> listUsers(Authentication authentication) {
        requireAdmin(authentication);
        return userRepository.findAllByOrderByIdAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public UserAccountResponse changeRole(Authentication authentication, Long userId, String requestedRole) {
        String effectiveRole = normalizeRequestedRole(requestedRole);
        User actor = requireAdmin(authentication);

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        String currentRole = target.getRole();
        if ("EMPLOYEE".equals(currentRole) && "USER".equals(effectiveRole)) {
            return toResponse(target, "USER");
        }
        if (("ADMIN".equals(currentRole) && "ADMIN".equals(effectiveRole))
                || ("USER".equals(currentRole) && "USER".equals(effectiveRole))) {
            return toResponse(target, effectiveRole);
        }

        if ("USER".equals(effectiveRole) && "ADMIN".equals(currentRole) && userRepository.countByRole("ADMIN") <= 1L) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The last administrator cannot be demoted");
        }

        java.util.Optional.ofNullable(roleManagementLockRepository.lockForRoleChange())
                .orElseGet(() -> java.util.Optional.of(new com.example.ems.entity.RoleManagementLock(1L)));
        User reloadedActor = userRepository.findById(actor.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account no longer exists"));
        if (!"ADMIN".equals(reloadedActor.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role is required");
        }

        if ("USER".equals(effectiveRole) && target.getId().equals(actor.getId()) && userRepository.countByRole("ADMIN") <= 1L) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The last administrator cannot be demoted");
        }

        target.setRole(effectiveRole);
        userRepository.saveAndFlush(target);
        return toResponse(target, effectiveRole);
    }

    private User requireAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account no longer exists"));
        if (!"ADMIN".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role is required");
        }
        return user;
    }

    private String normalizeRequestedRole(String role) {
        if (role == null || !("ADMIN".equals(role) || "USER".equals(role))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be USER or ADMIN");
        }
        return role;
    }

    private UserAccountResponse toResponse(User user) {
        return toResponse(user, displayRole(user.getRole()));
    }

    private UserAccountResponse toResponse(User user, String role) {
        Long employeeId = user.getEmployee() == null ? null : user.getEmployee().getId();
        return new UserAccountResponse(user.getId(), user.getFullName(), user.getEmail(), role, employeeId);
    }

    private String displayRole(String role) {
        if ("ADMIN".equals(role)) {
            return "ADMIN";
        }
        if ("USER".equals(role) || "EMPLOYEE".equals(role)) {
            return "USER";
        }
        return role;
    }
}
