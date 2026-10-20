package com.example.ems.controller;

import com.example.ems.dto.AccountResponse;
import com.example.ems.dto.EmployeeLinkRequest;
import com.example.ems.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
@Validated
public class AccountController {
    private final AccountService accounts;

    @GetMapping
    public List<AccountResponse> list() { return accounts.list(); }

    @PutMapping("/{accountId}/employee")
    public AccountResponse link(@PathVariable @Positive Long accountId,
                                @Valid @RequestBody EmployeeLinkRequest request) {
        return accounts.linkEmployee(accountId, request.employeeId());
    }
}
