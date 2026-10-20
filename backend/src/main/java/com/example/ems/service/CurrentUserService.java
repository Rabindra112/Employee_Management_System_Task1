package com.example.ems.service;

import com.example.ems.entity.User;
import com.example.ems.repository.UserRepository;
import com.example.ems.security.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CurrentUserService {
    private final UserRepository users;

    public User requireUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return users.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account no longer exists"));
    }

    @Transactional(readOnly = true)
    public void requireEmployeeAccess(Authentication authentication, Long employeeId) {
        User user = requireUser(authentication);
        String role = UserRole.normalize(user.getRole());
        if ("ADMIN".equals(role)) return;
        if (!"EMPLOYEE".equals(role) || user.getEmployee() == null
                || !user.getEmployee().getId().equals(employeeId)) {
            throw new AccessDeniedException("You can only access your own employee records");
        }
    }

    @Transactional(readOnly = true)
    public com.example.ems.entity.Employee ownEmployee(Authentication authentication) {
        User user = requireUser(authentication);
        if (user.getEmployee() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Your account has not been linked to an employee record. Please contact your administrator.");
        }
        // Initialize within this transaction because open-in-view is disabled.
        com.example.ems.entity.Employee employee = user.getEmployee();
        return new com.example.ems.entity.Employee(employee.getId(), employee.getName(),
                employee.getDepartment(), employee.getSalary());
    }

    @Transactional(readOnly = true)
    public User requireDocumentOwner(Authentication authentication) {
        User user = requireUser(authentication);
        if (!"EMPLOYEE".equals(UserRole.normalize(user.getRole()))) {
            throw new AccessDeniedException("Only employee accounts can upload documents");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public Long requireAccountId(Authentication authentication) {
        return requireUser(authentication).getId();
    }
}
