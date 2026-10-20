package com.example.ems.security;

public final class UserRole {
    private UserRole() {}

    /** Existing USER accounts retain employee access during the role migration. */
    public static String normalize(String role) {
        if ("ADMIN".equals(role)) return "ADMIN";
        if ("EMPLOYEE".equals(role) || "USER".equals(role)) return "EMPLOYEE";
        return "";
    }
}
