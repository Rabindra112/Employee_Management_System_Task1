package com.example.ems.service;

import com.example.ems.dto.AccountResponse;
import com.example.ems.entity.User;
import com.example.ems.exception.ResourceNotFoundException;
import com.example.ems.repository.EmployeeRepository;
import com.example.ems.repository.UserRepository;
import com.example.ems.security.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final UserRepository users;
    private final EmployeeRepository employees;

    @Transactional(readOnly = true)
    public List<AccountResponse> list() {
        return users.findAllByOrderByIdAsc().stream().map(this::response).toList();
    }

    @Transactional
    public AccountResponse linkEmployee(Long accountId, Long employeeId) {
        User user = users.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (!"EMPLOYEE".equals(UserRole.normalize(user.getRole()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only employee accounts can be linked");
        }
        if (employeeId == null) {
            user.setEmployee(null);
        } else {
            var employee = employees.findById(employeeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
            if (users.existsByEmployeeIdAndIdNot(employeeId, accountId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee is already linked to another account");
            }
            user.setEmployee(employee);
        }
        return response(users.saveAndFlush(user));
    }

    private AccountResponse response(User user) {
        return new AccountResponse(user.getId(), user.getFullName(), user.getEmail(),
                UserRole.normalize(user.getRole()), user.getEmployee() == null ? null : user.getEmployee().getId());
    }
}
