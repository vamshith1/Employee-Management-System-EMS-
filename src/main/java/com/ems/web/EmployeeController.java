package com.ems.web;

import com.ems.domain.EmployeeStatus;
import com.ems.dto.EmployeeDtos.EmployeeRequest;
import com.ems.dto.EmployeeDtos.EmployeeResponse;
import com.ems.dto.EmployeeDtos.EmployeeSearchCriteria;
import com.ems.dto.PageResponse;
import com.ems.security.CurrentUser;
import com.ems.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final CurrentUser currentUser;

    public EmployeeController(EmployeeService employeeService, CurrentUser currentUser) {
        this.employeeService = employeeService;
        this.currentUser = currentUser;
    }

    /**
     * Search with optional filters, e.g.
     * {@code /api/employees?q=ali&departmentId=1&status=ACTIVE&page=0&size=10&sort=hireDate,desc}
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public PageResponse<EmployeeResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) EmployeeStatus status,
            @RequestParam(required = false) String jobTitle,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hiredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hiredTo,
            Pageable pageable) {
        return employeeService.search(
                new EmployeeSearchCriteria(q, departmentId, status, jobTitle, hiredFrom, hiredTo), pageable);
    }

    /** The signed-in user's own employee profile. */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public EmployeeResponse me() {
        return employeeService.findById(currentUser.requireEmployeeId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public EmployeeResponse get(@PathVariable Long id) {
        return employeeService.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody EmployeeRequest request) {
        EmployeeResponse created = employeeService.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public EmployeeResponse update(@PathVariable Long id, @Valid @RequestBody EmployeeRequest request) {
        return employeeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
