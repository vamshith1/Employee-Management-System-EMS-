package com.ems.dto;

import com.ems.domain.Employee;
import com.ems.domain.EmployeeStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class EmployeeDtos {

    private EmployeeDtos() {
    }

    public record EmployeeRequest(
            @NotBlank(message = "First name is required")
            @Size(max = 60, message = "First name must be at most 60 characters")
            String firstName,
            @NotBlank(message = "Last name is required")
            @Size(max = 60, message = "Last name must be at most 60 characters")
            String lastName,
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be a valid email address")
            @Size(max = 120, message = "Email must be at most 120 characters")
            String email,
            @Pattern(regexp = "^\\+?[0-9 ()-]{7,20}$", message = "Phone must be 7-20 digits, optionally with +, spaces, () or -")
            String phone,
            @NotBlank(message = "Job title is required")
            @Size(max = 100, message = "Job title must be at most 100 characters")
            String jobTitle,
            @PositiveOrZero(message = "Salary cannot be negative")
            @Digits(integer = 10, fraction = 2, message = "Salary must have at most 10 integer digits and 2 decimals")
            BigDecimal salary,
            @NotNull(message = "Hire date is required")
            @PastOrPresent(message = "Hire date cannot be in the future")
            LocalDate hireDate,
            EmployeeStatus status,
            @NotNull(message = "Department is required")
            Long departmentId) {
    }

    /** Filters for GET /api/employees. All are optional and combined with AND. */
    public record EmployeeSearchCriteria(
            String q,
            Long departmentId,
            EmployeeStatus status,
            String jobTitle,
            LocalDate hiredFrom,
            LocalDate hiredTo) {
    }

    public record EmployeeResponse(
            Long id,
            String firstName,
            String lastName,
            String fullName,
            String email,
            String phone,
            String jobTitle,
            BigDecimal salary,
            LocalDate hireDate,
            EmployeeStatus status,
            Long departmentId,
            String departmentName,
            Instant createdAt,
            Instant updatedAt) {

        public static EmployeeResponse from(Employee e) {
            var d = e.getDepartment();
            return new EmployeeResponse(e.getId(), e.getFirstName(), e.getLastName(), e.getFullName(),
                    e.getEmail(), e.getPhone(), e.getJobTitle(), e.getSalary(), e.getHireDate(), e.getStatus(),
                    d.getId(), d.getName(), e.getCreatedAt(), e.getUpdatedAt());
        }
    }
}
