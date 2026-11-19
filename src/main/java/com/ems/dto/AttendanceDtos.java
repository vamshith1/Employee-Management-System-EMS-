package com.ems.dto;

import com.ems.domain.Attendance;
import com.ems.domain.AttendanceStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class AttendanceDtos {

    private AttendanceDtos() {
    }

    public record CheckInOutRequest(
            @Size(max = 255, message = "Notes must be at most 255 characters") String notes) {
    }

    /** Manual attendance entry or correction by HR/Admin. */
    public record AttendanceRequest(
            @NotNull(message = "Employee is required") Long employeeId,
            @NotNull(message = "Work date is required")
            @PastOrPresent(message = "Work date cannot be in the future")
            LocalDate workDate,
            LocalDateTime checkIn,
            LocalDateTime checkOut,
            @NotNull(message = "Status is required") AttendanceStatus status,
            @Size(max = 255, message = "Notes must be at most 255 characters") String notes) {

        @AssertTrue(message = "Check-out must be after check-in")
        public boolean isCheckOutAfterCheckIn() {
            return checkIn == null || checkOut == null || checkOut.isAfter(checkIn);
        }

        @AssertTrue(message = "Check-out requires a check-in")
        public boolean isCheckOutHasCheckIn() {
            return checkOut == null || checkIn != null;
        }
    }

    public record AttendanceResponse(
            Long id,
            Long employeeId,
            String employeeName,
            LocalDate workDate,
            LocalDateTime checkIn,
            LocalDateTime checkOut,
            AttendanceStatus status,
            Double hoursWorked,
            String notes) {

        public static AttendanceResponse from(Attendance a) {
            Double hours = null;
            if (a.getCheckIn() != null && a.getCheckOut() != null) {
                long minutes = Duration.between(a.getCheckIn(), a.getCheckOut()).toMinutes();
                hours = Math.round(minutes / 60.0 * 100) / 100.0;
            }
            var e = a.getEmployee();
            return new AttendanceResponse(a.getId(), e.getId(), e.getFullName(), a.getWorkDate(),
                    a.getCheckIn(), a.getCheckOut(), a.getStatus(), hours, a.getNotes());
        }
    }
}
