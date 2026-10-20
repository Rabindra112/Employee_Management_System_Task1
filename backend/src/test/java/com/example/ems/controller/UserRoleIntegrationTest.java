package com.example.ems.controller;

import com.example.ems.entity.Employee;
import com.example.ems.entity.RoleManagementLock;
import com.example.ems.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserRoleIntegrationTest extends AuthenticatedIntegrationTestSupport {

    @BeforeEach
    void setUpRoleManagement() {
        when(roleManagementLockRepository.lockForRoleChange())
                .thenReturn(Optional.of(new RoleManagementLock(1L)));
        when(userRepository.findById(currentUser.getId())).thenReturn(Optional.of(currentUser));
        when(userRepository.countByRole("ADMIN")).thenReturn(1L);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void anonymousUsersCannotListAccountsOrChangeRoles() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/admin/users/8/role")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(roleManagementLockRepository);
        verify(userRepository, never()).findAllByOrderByIdAsc();
        verifyNoAccountSaved();
    }

    @Test
    void bothEmployeeAndLegacyUserAreForbiddenEvenWithAdminTokenClaim() throws Exception {
        String authorization = authorization("ADMIN");
        for (String storedRole : List.of("EMPLOYEE", "USER")) {
            useEmployeeAccount(storedRole, null);
            mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, authorization))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/api/admin/users/8/role").header(HttpHeaders.AUTHORIZATION, authorization)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                    .andExpect(status().isForbidden());
        }

        verifyNoInteractions(roleManagementLockRepository);
        verifyNoAccountSaved();
    }

    @Test
    void adminListUsesRequestedRoleNamesAndOmitsCredentials() throws Exception {
        User employee = account(8L, "EMPLOYEE");
        employee.setEmployee(employeeRecord());
        User legacyUser = account(9L, "USER");
        when(userRepository.findAllByOrderByIdAsc()).thenReturn(List.of(currentUser, employee, legacyUser));

        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, authorization("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].fullName").value("Riya Sharma"))
                .andExpect(jsonPath("$[0].email").value(EMAIL))
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[0].employeeId").isEmpty())
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$[1].role").value("USER"))
                .andExpect(jsonPath("$[1].employeeId").value(11))
                .andExpect(jsonPath("$[1].password").doesNotExist())
                .andExpect(jsonPath("$[1].employee").doesNotExist())
                .andExpect(jsonPath("$[2].role").value("USER"));

        verifyNoAccountSaved();
    }

    @Test
    void adminCanPromoteAnEmployeeWithoutLosingEmployeeLink() throws Exception {
        User target = account(8L, "EMPLOYEE");
        Employee employee = employeeRecord();
        target.setEmployee(employee);
        when(userRepository.findById(8L)).thenReturn(Optional.of(target));

        mvc.perform(patch("/api/admin/users/8/role").header(HttpHeaders.AUTHORIZATION, authorization("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.employeeId").value(11))
                .andExpect(jsonPath("$.password").doesNotExist());

        assertEquals("ADMIN", target.getRole());
        assertSame(employee, target.getEmployee());
        verify(userRepository).saveAndFlush(target);
    }

    @Test
    void adminCanDemoteAnotherAdminToUserWithoutLosingEmployeeLink() throws Exception {
        User target = account(8L, "ADMIN");
        Employee employee = employeeRecord();
        target.setEmployee(employee);
        when(userRepository.findById(8L)).thenReturn(Optional.of(target));
        when(userRepository.countByRole("ADMIN")).thenReturn(2L);

        mvc.perform(patch("/api/admin/users/8/role").header(HttpHeaders.AUTHORIZATION, authorization("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.employeeId").value(11));

        assertSame(employee, target.getEmployee());
        assertEquals("USER", target.getRole());
        verify(userRepository).saveAndFlush(target);
    }

    @Test
    void lastAdminCannotDemoteTheirOwnAccount() throws Exception {
        mvc.perform(patch("/api/admin/users/7/role").header(HttpHeaders.AUTHORIZATION, authorization("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isConflict());

        assertEquals("ADMIN", currentUser.getRole());
        verifyNoAccountSaved();
    }

    @Test
    void adminCanDemoteSelfWhenAnotherAdminRemainsAndExistingTokenImmediatelyLosesAccess() throws Exception {
        when(userRepository.countByRole("ADMIN")).thenReturn(2L);
        String existingAuthorization = authorization("ADMIN");

        mvc.perform(patch("/api/admin/users/7/role")
                        .header(HttpHeaders.AUTHORIZATION, existingAuthorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));
        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, existingAuthorization))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, existingAuthorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));

        verify(userRepository).saveAndFlush(currentUser);
    }

    @Test
    void promotionAppliesToAlreadyIssuedEmployeeToken() throws Exception {
        useEmployeeAccount("USER", null);
        String existingAuthorization = authorization("USER");
        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, existingAuthorization))
                .andExpect(status().isForbidden());

        // Another administrator has updated the database while this user retains the same JWT.
        currentUser.setRole("ADMIN");
        when(userRepository.findAllByOrderByIdAsc()).thenReturn(List.of(currentUser));
        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, existingAuthorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("ADMIN"));
    }

    @Test
    void sameAdminRoleIsASuccessfulNoOpEvenForTheLastAdmin() throws Exception {
        mvc.perform(patch("/api/admin/users/7/role").header(HttpHeaders.AUTHORIZATION, authorization("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        assertEquals("ADMIN", currentUser.getRole());
        verifyNoAccountSaved();
    }

    @Test
    void legacyEmployeeMappedToUserIsASuccessfulNoOpWithoutRewritingDatabase() throws Exception {
        User target = account(8L, "EMPLOYEE");
        when(userRepository.findById(8L)).thenReturn(Optional.of(target));

        mvc.perform(patch("/api/admin/users/8/role").header(HttpHeaders.AUTHORIZATION, authorization("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));

        assertEquals("EMPLOYEE", target.getRole());
        verifyNoAccountSaved();
    }

    @Test
    void missingTargetAccountReturnsNotFoundWithoutMutation() throws Exception {
        mvc.perform(patch("/api/admin/users/999/role").header(HttpHeaders.AUTHORIZATION, authorization("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isNotFound());

        verifyNoAccountSaved();
    }

    @Test
    void roleMustBeExactlyUserOrAdminAndCannotBeNullOrMissing() throws Exception {
        String authorization = authorization("ADMIN");
        for (String body : List.of("{\"role\":\"EMPLOYEE\"}", "{\"role\":\"SUPER_ADMIN\"}",
                "{\"role\":\"admin\"}", "{\"role\":\" ADMIN \"}", "{\"role\":\"\"}",
                "{\"role\":null}", "{}", "null")) {
            mvc.perform(patch("/api/admin/users/8/role").header(HttpHeaders.AUTHORIZATION, authorization)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(roleManagementLockRepository);
        verify(userRepository, never()).findById(any());
        verifyNoAccountSaved();
    }

    @Test
    void roleUpdateRechecksRequesterAfterWaitingForRoleChangeLock() throws Exception {
        User target = account(8L, "USER");
        when(userRepository.findById(8L)).thenReturn(Optional.of(target));
        when(roleManagementLockRepository.lockForRoleChange()).thenAnswer(invocation -> {
            // Simulate another request demoting the actor before this transaction acquires the lock.
            currentUser.setRole("USER");
            return Optional.of(new RoleManagementLock(1L));
        });

        mvc.perform(patch("/api/admin/users/8/role").header(HttpHeaders.AUTHORIZATION, authorization("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        assertEquals("USER", target.getRole());
        verify(roleManagementLockRepository).lockForRoleChange();
        verifyNoAccountSaved();
    }

    @Test
    void browserCanPreflightPatchRoleUpdate() throws Exception {
        mvc.perform(options("/api/admin/users/8/role")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5174")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5174"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        org.hamcrest.Matchers.containsString("PATCH")));
    }

    private User account(Long id, String role) {
        User account = new User();
        account.setId(id);
        account.setEmail("user" + id + "@example.com");
        account.setFullName("User " + id);
        account.setPassword("must-never-be-returned");
        account.setRole(role);
        return account;
    }

    private Employee employeeRecord() {
        return new Employee(11L, "Employee", "IT", new BigDecimal("45000.00"));
    }

    private void verifyNoAccountSaved() {
        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }
}
