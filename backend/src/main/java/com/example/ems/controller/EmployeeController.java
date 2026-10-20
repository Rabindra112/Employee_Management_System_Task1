package com.example.ems.controller;

import com.example.ems.dto.EmployeeRequest;
import com.example.ems.entity.Employee;
import com.example.ems.service.EmployeeService;
import com.example.ems.service.CurrentUserService;
import org.springframework.security.core.Authentication;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/employees")
@Validated
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "${app.frontend-origin:http://localhost:5174}")
public class EmployeeController {

    private final EmployeeService service;

    private final CurrentUserService currentUsers;

    @GetMapping("/me")
    public Employee ownEmployee(Authentication authentication) {
        return currentUsers.ownEmployee(authentication);
    }

    // CREATE EMPLOYEE
    @PostMapping
    public ResponseEntity<Employee> create(
            @Valid @RequestBody EmployeeRequest request) {

        log.info("Request received to create employee");

        Employee employee = service.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(employee);
    }

    // GET ALL EMPLOYEES WITH SEARCH AND PAGINATION
    @GetMapping
    public Page<Employee> list(
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String department,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {

        log.info("Fetching employees - page: {}, size: {}", page, size);

        return service.list(name, department, page, size);
    }

    // EMPLOYEE DASHBOARD STATISTICS
    @GetMapping("/stats")
    public Map<String, Object> stats() {

        log.info("Fetching employee statistics");

        return service.stats();
    }

    // GET EMPLOYEE BY ID
    @GetMapping("/{id}")
    public Employee get(@PathVariable @Positive Long id) {

        log.info("Fetching employee with ID: {}", id);

        return service.get(id);
    }

    // UPDATE EMPLOYEE
    @PutMapping("/{id}")
    public Employee update(
            @PathVariable @Positive Long id,
            @Valid @RequestBody EmployeeRequest request) {

        log.info("Updating employee with ID: {}", id);

        return service.update(id, request);
    }

    // DELETE EMPLOYEE
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable @Positive Long id) {

        log.info("Deleting employee with ID: {}", id);

        service.delete(id);

        return ResponseEntity.noContent().build();
    }
}
