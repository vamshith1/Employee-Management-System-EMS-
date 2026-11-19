package com.ems.web;

import com.ems.domain.AttendanceStatus;
import com.ems.dto.AttendanceDtos.AttendanceRequest;
import com.ems.dto.AttendanceDtos.AttendanceResponse;
import com.ems.dto.AttendanceDtos.CheckInOutRequest;
import com.ems.dto.PageResponse;
import com.ems.security.CurrentUser;
import com.ems.service.AttendanceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final CurrentUser currentUser;

    public AttendanceController(AttendanceService attendanceService, CurrentUser currentUser) {
        this.attendanceService = attendanceService;
        this.currentUser = currentUser;
    }

    @PostMapping("/check-in")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AttendanceResponse> checkIn(@Valid @RequestBody(required = false) CheckInOutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attendanceService.checkIn(currentUser.requireEmployeeId(), request == null ? null : request.notes()));
    }

    @PostMapping("/check-out")
    @PreAuthorize("isAuthenticated()")
    public AttendanceResponse checkOut(@Valid @RequestBody(required = false) CheckInOutRequest request) {
        return attendanceService.checkOut(currentUser.requireEmployeeId(), request == null ? null : request.notes());
    }

    /** The signed-in employee's own attendance history. */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<AttendanceResponse> mine(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable) {
        return attendanceService.search(currentUser.requireEmployeeId(), from, to, null, pageable);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public PageResponse<AttendanceResponse> search(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AttendanceStatus status,
            Pageable pageable) {
        return attendanceService.search(employeeId, from, to, status, pageable);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<AttendanceResponse> create(@Valid @RequestBody AttendanceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attendanceService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public AttendanceResponse update(@PathVariable Long id, @Valid @RequestBody AttendanceRequest request) {
        return attendanceService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        attendanceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
