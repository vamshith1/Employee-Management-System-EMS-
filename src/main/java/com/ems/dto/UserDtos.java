package com.ems.dto;

import com.ems.domain.Role;
import com.ems.domain.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class UserDtos {

    private UserDtos() {
    }

    public record CreateUserRequest(
            @NotBlank(message = "Username is required")
            @Size(min = 3, max = 50, message = "Username must be 3-50 characters")
            @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username may only contain letters, digits, '.', '_' and '-'")
            String username,
            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 100, message = "Password must be 8-100 characters")
            String password,
            @NotNull(message = "Role is required") Role role,
            Long employeeId) {
    }

    public record UpdateUserRequest(
            @NotNull(message = "Role is required") Role role,
            @NotNull(message = "Enabled flag is required") Boolean enabled,
            Long employeeId) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 100, message = "Password must be 8-100 characters")
            String password) {
    }

    public record UserResponse(
            Long id,
            String username,
            Role role,
            boolean enabled,
            Long employeeId,
            String employeeName,
            Instant createdAt) {

        public static UserResponse from(User u) {
            var e = u.getEmployee();
            return new UserResponse(u.getId(), u.getUsername(), u.getRole(), u.isEnabled(),
                    e == null ? null : e.getId(), e == null ? null : e.getFullName(), u.getCreatedAt());
        }
    }
}
