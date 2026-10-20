package com.example.ems.controller;

import com.example.ems.dto.UserAccountResponse;
import com.example.ems.dto.UserRoleUpdateRequest;
import com.example.ems.service.UserRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class UserController {
    private final UserRoleService userRoleService;

    @GetMapping("/users")
    public List<UserAccountResponse> listUsers(Authentication authentication) {
        return userRoleService.listUsers(authentication);
    }

    @PatchMapping("/users/{userId}/role")
    public UserAccountResponse updateRole(
            Authentication authentication,
            @PathVariable Long userId,
            @RequestBody(required = false) UserRoleUpdateRequest request) {
        if (request == null || request.role() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be USER or ADMIN");
        }
        return userRoleService.changeRole(authentication, userId, request.role());
    }

    @RequestMapping(value = "/users/{userId}/role", method = RequestMethod.OPTIONS)
    public void preflightRoleUpdate() {
        // Spring handles the CORS preflight through the global configuration.
    }
}
