package com.example.ems.service;

import com.example.ems.dto.EmployeeRequest;
import com.example.ems.entity.Employee;
import com.example.ems.exception.ResourceNotFoundException;
import com.example.ems.repository.EmployeeRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmployeeServiceTest {

    private EmployeeRepository repository;
    private EmployeeService service;

    @BeforeEach
    void setup() {
        repository = mock(EmployeeRepository.class);
        service = new EmployeeService(repository);
    }

    // =========================================
    // TEST 1: CREATE EMPLOYEE
    // =========================================

    @Test
    void createEmployee() {

        EmployeeRequest request = new EmployeeRequest(
                "Alice",
                "IT",
                new BigDecimal("45000")
        );

        when(repository.save(any(Employee.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Employee result = service.create(request);

        assertNotNull(result);
        assertEquals("Alice", result.getName());
        assertEquals("IT", result.getDepartment());
        assertEquals(
                0,
                new BigDecimal("45000").compareTo(result.getSalary())
        );

        verify(repository, times(1))
                .save(any(Employee.class));
    }

    // =========================================
    // TEST 2: GET EXISTING EMPLOYEE
    // =========================================

    @Test
    void getEmployeeById() {

        Employee employee = new Employee();

        employee.setId(1L);
        employee.setName("Alice");
        employee.setDepartment("IT");
        employee.setSalary(new BigDecimal("45000"));

        when(repository.findById(1L))
                .thenReturn(Optional.of(employee));

        Employee result = service.get(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Alice", result.getName());

        verify(repository).findById(1L);
    }

    // =========================================
    // TEST 3: EMPLOYEE NOT FOUND
    // =========================================

    @Test
    void missingEmployeeThrows404Exception() {

        when(repository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> service.get(99L)
        );

        assertEquals(
                "Employee not found with ID: 99",
                exception.getMessage()
        );

        verify(repository).findById(99L);
    }

    // =========================================
    // TEST 4: UPDATE EMPLOYEE
    // =========================================

    @Test
    void updateEmployee() {

        Employee employee = new Employee();

        employee.setId(1L);
        employee.setName("Alice");
        employee.setDepartment("IT");
        employee.setSalary(new BigDecimal("45000"));

        EmployeeRequest request = new EmployeeRequest(
                "Alice Sharma",
                "HR",
                new BigDecimal("55000")
        );

        when(repository.findById(1L))
                .thenReturn(Optional.of(employee));

        when(repository.save(any(Employee.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Employee result = service.update(1L, request);

        assertEquals("Alice Sharma", result.getName());
        assertEquals("HR", result.getDepartment());

        assertEquals(
                0,
                new BigDecimal("55000").compareTo(result.getSalary())
        );

        verify(repository).findById(1L);
        verify(repository).save(employee);
    }

    // =========================================
    // TEST 5: DELETE EMPLOYEE
    // =========================================

    @Test
    void deleteEmployee() {

        Employee employee = new Employee();
        employee.setId(1L);

        when(repository.findById(1L))
                .thenReturn(Optional.of(employee));

        service.delete(1L);

        verify(repository, times(1))
                .delete(employee);
    }

    // =========================================
    // TEST 6: DELETE NON-EXISTING EMPLOYEE
    // =========================================

    @Test
    void deleteMissingEmployee() {

        when(repository.findById(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.delete(99L)
        );

        verify(repository, never())
                .delete(any(Employee.class));
    }

    // =========================================
    // TEST 7: GET EMPLOYEE LIST
    // =========================================

    @Test
    void listEmployees() {

        Employee employee = new Employee();

        employee.setId(1L);
        employee.setName("Alice");
        employee.setDepartment("IT");
        employee.setSalary(new BigDecimal("45000"));

        Page<Employee> employeePage = new PageImpl<>(
                List.of(employee)
        );

        when(
                repository
                        .findByNameContainingIgnoreCaseAndDepartmentContainingIgnoreCase(
                                eq("Alice"),
                                eq("IT"),
                                any(Pageable.class)
                        )
        ).thenReturn(employeePage);

        Page<Employee> result = service.list(
                "Alice",
                "IT",
                0,
                10
        );

        assertEquals(1, result.getTotalElements());
        assertEquals("Alice", result.getContent().get(0).getName());

        verify(repository)
                .findByNameContainingIgnoreCaseAndDepartmentContainingIgnoreCase(
                        eq("Alice"),
                        eq("IT"),
                        any(Pageable.class)
                );
    }
}