package com.example.ems.dto;

public record UserAccountResponse(Long id, String fullName, String email, String role, Long employeeId) {}
