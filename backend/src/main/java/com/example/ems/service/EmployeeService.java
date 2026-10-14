package com.example.ems.service;

import com.example.ems.dto.EmployeeRequest;
import com.example.ems.entity.Employee;
import com.example.ems.exception.ResourceNotFoundException;
import com.example.ems.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeService {

    private final EmployeeRepository repository;

    // CREATE EMPLOYEE
    @Transactional
    public Employee create(EmployeeRequest request) {

        log.info("Creating new employee");

        Employee employee = new Employee();

        apply(employee, request);

        Employee savedEmployee = repository.save(employee);

        log.info("Employee created successfully with ID: {}", savedEmployee.getId());

        return savedEmployee;
    }

    // UPDATE EMPLOYEE
    @Transactional
    public Employee update(Long id, EmployeeRequest request) {

        log.info("Updating employee with ID: {}", id);

        Employee employee = get(id);

        apply(employee, request);

        Employee updatedEmployee = repository.save(employee);

        log.info("Employee updated successfully with ID: {}", id);

        return updatedEmployee;
    }

    // DELETE EMPLOYEE
    @Transactional
    public void delete(Long id) {

        log.info("Deleting employee with ID: {}", id);

        Employee employee = get(id);

        repository.delete(employee);

        log.info("Employee deleted successfully with ID: {}", id);
    }

    // GET EMPLOYEE BY ID
    @Transactional(readOnly = true)
    public Employee get(Long id) {

        log.info("Fetching employee with ID: {}", id);

        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found with ID: " + id
                ));
    }

    // GET ALL EMPLOYEES WITH SEARCH, FILTER AND PAGINATION
    @Transactional(readOnly = true)
    public Page<Employee> list(
            String name,
            String department,
            int page,
            int size) {

        log.info("Fetching employees - page: {}, size: {}", page, size);

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "id")
        );

        return repository
                .findByNameContainingIgnoreCaseAndDepartmentContainingIgnoreCase(
                        name.trim(),
                        department.trim(),
                        pageable
                );
    }

    // DASHBOARD STATISTICS
    @Transactional(readOnly = true)
    public Map<String, Object> stats() {

        log.info("Fetching employee dashboard statistics");

        var employees = repository.findAll();

        // Calculate total salary
        BigDecimal totalSalary = employees.stream()
                .map(Employee::getSalary)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Count unique departments
        long totalDepartments = employees.stream()
                .map(employee -> employee.getDepartment().trim().toLowerCase())
                .distinct()
                .count();

        log.info("Dashboard statistics fetched successfully");

        return Map.of(
                "totalEmployees", employees.size(),
                "totalDepartments", totalDepartments,
                "totalSalary", totalSalary
        );
    }

    // COMMON METHOD FOR CREATE AND UPDATE
    private void apply(Employee employee, EmployeeRequest request) {

        employee.setName(request.name().trim());
        employee.setDepartment(request.department().trim());
        employee.setSalary(request.salary());
    }
}