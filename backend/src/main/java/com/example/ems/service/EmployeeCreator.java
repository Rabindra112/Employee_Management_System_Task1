package com.example.ems.service;

import com.example.ems.dto.EmployeeRequest;
import com.example.ems.entity.Employee;

public interface EmployeeCreator {
    Employee create(EmployeeRequest request);
}
