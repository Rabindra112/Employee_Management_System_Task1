package com.example.ems.dto;

import jakarta.validation.constraints.*;

public record SignupRequest(@NotBlank @Size(max = 100) String fullName, @NotBlank @Email String email,
                            @NotBlank @Size(min = 8, max = 72) String password, @NotBlank String confirmPassword) {
}
