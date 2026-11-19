package com.ems.web;

import com.ems.domain.LeaveStatus;
import com.ems.dto.LeaveDtos.LeaveDecisionRequest;
import com.ems.dto.LeaveDtos.LeaveRequestCreate;
import com.ems.dto.LeaveDtos.LeaveResponse;
import com.ems.dto.PageResponse;
import com.ems.security.CurrentUser;
import com.ems.service.LeaveService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leaves")
public class LeaveController {

    private final LeaveService leaveService;
    private final CurrentUser currentUser;

    public LeaveController(LeaveService leaveService, CurrentUser currentUser) {
        this.leaveService = leaveService;
        this.currentUser = currentUser;
    }

    /** Submit a leave request for yourself. */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LeaveResponse> submit(@Valid @RequestBody LeaveRequestCreate request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(leaveService.submit(currentUser.requireEmployeeId(), request));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<LeaveResponse> mine(@RequestParam(required = false) LeaveStatus status, Pageable pageable) {
        return leaveService.search(currentUser.requireEmployeeId(), status, pageable);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public LeaveResponse cancel(@PathVariable Long id) {
        return leaveService.cancel(id, currentUser.requireEmployeeId());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public PageResponse<LeaveResponse> search(@RequestParam(required = false) Long employeeId,
                                              @RequestParam(required = false) LeaveStatus status,
                                              Pageable pageable) {
        return leaveService.search(employeeId, status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public LeaveResponse get(@PathVariable Long id) {
        return leaveService.findById(id);
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public LeaveResponse approve(@PathVariable Long id, @Valid @RequestBody(required = false) LeaveDecisionRequest body) {
        return leaveService.approve(id, currentUser.get().getId(), body == null ? null : body.comment());
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public LeaveResponse reject(@PathVariable Long id, @Valid @RequestBody(required = false) LeaveDecisionRequest body) {
        return leaveService.reject(id, currentUser.get().getId(), body == null ? null : body.comment());
    }
}
