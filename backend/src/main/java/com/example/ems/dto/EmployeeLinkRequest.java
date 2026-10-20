package com.example.ems.dto;

import jakarta.validation.constraints.Positive;

public record EmployeeLinkRequest(@Positive Long employeeId) {}
