package com.example.ems.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentRejectionRequest(
        @NotBlank @Size(max = 1000) String reason
) {}
