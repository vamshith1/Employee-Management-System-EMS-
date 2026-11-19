package com.ems.dto;

import com.ems.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Username is required") String username,
            @NotBlank(message = "Password is required") String password) {
    }

    public record LoginResponse(
            String accessToken,
            String tokenType,
            long expiresInSeconds,
            CurrentUserResponse user) {
    }

    public record CurrentUserResponse(
            Long id,
            String username,
            Role role,
            Long employeeId,
            String employeeName) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required") String currentPassword,
            @NotBlank(message = "New password is required")
            @Size(min = 8, max = 100, message = "New password must be 8-100 characters") String newPassword) {
    }
}
