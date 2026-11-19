package com.ems;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies authentication and the role matrix (Admin / HR / Employee) end to end. */
class SecurityIntegrationTest extends IntegrationTestSupport {

    @Test
    void loginReturnsJwtAndProfile() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "hr", "password", "Hr@12345"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("HR"))
                .andExpect(jsonPath("$.user.employeeName").value("Priya Sharma"));
    }

    @Test
    void wrongPasswordIs401WithJsonError() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "admin", "password", "nope"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void loginValidatesBody() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    void missingOrInvalidTokenIs401() throws Exception {
        mvc.perform(get("/api/employees/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.path").value("/api/employees/me"));
        mvc.perform(get("/api/employees/me").header("Authorization", "Bearer not.a.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeeCanSeeOwnDataOnly() throws Exception {
        String token = employeeToken();

        mvc.perform(auth(get("/api/employees/me"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Arjun Mehta"));
        mvc.perform(auth(get("/api/attendance/me"), token)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/leaves/me"), token)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/departments"), token)).andExpect(status().isOk());

        mvc.perform(auth(get("/api/employees"), token)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/employees/1"), token)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/attendance"), token)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/leaves"), token)).andExpect(status().isForbidden());
        mvc.perform(auth(put("/api/leaves/1/approve"), token)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/dashboard"), token)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/users"), token)).andExpect(status().isForbidden());
        mvc.perform(auth(delete("/api/employees/1"), token)).andExpect(status().isForbidden());
    }

    @Test
    void hrManagesEmployeesButNotUsersOrDepartments() throws Exception {
        String token = hrToken();

        mvc.perform(auth(get("/api/employees"), token)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/attendance"), token)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/leaves"), token)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/dashboard"), token)).andExpect(status().isOk());

        mvc.perform(auth(get("/api/users"), token)).andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/departments"), token).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Legal"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void adminCanManageUsersAndDepartments() throws Exception {
        String token = adminToken();

        mvc.perform(auth(get("/api/users"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        mvc.perform(auth(get("/api/employees"), token)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/dashboard"), token)).andExpect(status().isOk());
    }

    @Test
    void adminWithoutEmployeeRecordGetsClearErrorOnSelfService() throws Exception {
        mvc.perform(auth(get("/api/employees/me"), adminToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Your account is not linked to an employee record"));
    }
}
