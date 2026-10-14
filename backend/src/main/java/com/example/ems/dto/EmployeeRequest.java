package com.example.ems.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record EmployeeRequest(
        @NotBlank(message = "Name is required") @Size(max = 100) String name,
        @NotBlank(message = "Department is required") @Size(max = 80) String department,
        @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "Salary must be greater than zero") @Digits(integer = 10, fraction = 2) BigDecimal salary
) {
}
