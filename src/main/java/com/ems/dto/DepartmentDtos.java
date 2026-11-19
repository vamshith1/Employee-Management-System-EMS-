package com.ems.dto;

import com.ems.domain.Department;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class DepartmentDtos {

    private DepartmentDtos() {
    }

    public record DepartmentRequest(
            @NotBlank(message = "Department name is required")
            @Size(max = 100, message = "Department name must be at most 100 characters")
            String name,
            @Size(max = 500, message = "Description must be at most 500 characters")
            String description) {
    }

    public record DepartmentResponse(Long id, String name, String description, Instant createdAt) {

        public static DepartmentResponse from(Department d) {
            return new DepartmentResponse(d.getId(), d.getName(), d.getDescription(), d.getCreatedAt());
        }
    }
}
