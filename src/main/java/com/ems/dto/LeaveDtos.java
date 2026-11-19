package com.ems.dto;

import com.ems.domain.LeaveRequest;
import com.ems.domain.LeaveStatus;
import com.ems.domain.LeaveType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

public final class LeaveDtos {

    private LeaveDtos() {
    }

    public record LeaveRequestCreate(
            @NotNull(message = "Leave type is required") LeaveType type,
            @NotNull(message = "Start date is required") LocalDate startDate,
            @NotNull(message = "End date is required") LocalDate endDate,
            @NotBlank(message = "Reason is required")
            @Size(max = 500, message = "Reason must be at most 500 characters") String reason) {

        @AssertTrue(message = "End date must be on or after start date")
        public boolean isDateRangeValid() {
            return startDate == null || endDate == null || !endDate.isBefore(startDate);
        }
    }

    public record LeaveDecisionRequest(
            @Size(max = 500, message = "Comment must be at most 500 characters") String comment) {
    }

    public record LeaveResponse(
            Long id,
            Long employeeId,
            String employeeName,
            LeaveType type,
            LocalDate startDate,
            LocalDate endDate,
            long days,
            String reason,
            LeaveStatus status,
            String reviewedBy,
            String reviewComment,
            Instant reviewedAt,
            Instant createdAt) {

        public static LeaveResponse from(LeaveRequest l) {
            var e = l.getEmployee();
            var reviewer = l.getReviewedBy();
            return new LeaveResponse(l.getId(), e.getId(), e.getFullName(), l.getType(), l.getStartDate(),
                    l.getEndDate(), l.getDays(), l.getReason(), l.getStatus(),
                    reviewer == null ? null : reviewer.getUsername(), l.getReviewComment(), l.getReviewedAt(),
                    l.getCreatedAt());
        }
    }
}
