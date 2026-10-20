package com.example.ems.dto;

import jakarta.validation.constraints.*;

public record SignupRequest(

        @NotBlank
        @Size(max = 100)
        String fullName,

        @NotBlank
        @Email
        @Size(max = 190)
        String email,

        @NotBlank
        String encryptedPassword,

        @NotBlank
        String encryptedConfirmPassword

) {}
