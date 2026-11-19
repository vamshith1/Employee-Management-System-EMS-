package com.ems.service;

import com.ems.domain.Employee;
import com.ems.domain.EmployeeStatus;
import com.ems.domain.LeaveRequest;
import com.ems.domain.LeaveStatus;
import com.ems.domain.User;
import com.ems.dto.LeaveDtos.LeaveRequestCreate;
import com.ems.dto.LeaveDtos.LeaveResponse;
import com.ems.dto.PageResponse;
import com.ems.exception.BusinessException;
import com.ems.exception.ResourceNotFoundException;
import com.ems.repository.LeaveRequestRepository;
import com.ems.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;

@Service
public class LeaveService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
    private static final EnumSet<LeaveStatus> ACTIVE_STATES = EnumSet.of(LeaveStatus.PENDING, LeaveStatus.APPROVED);
    private static final int MAX_DAYS = 90;

    private final LeaveRequestRepository leaveRepository;
    private final EmployeeService employeeService;
    private final UserRepository userRepository;
    private final Clock clock;

    public LeaveService(LeaveRequestRepository leaveRepository, EmployeeService employeeService,
                        UserRepository userRepository, Clock clock) {
        this.leaveRepository = leaveRepository;
        this.employeeService = employeeService;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public LeaveResponse submit(Long employeeId, LeaveRequestCreate request) {
        Employee employee = employeeService.get(employeeId);
        if (employee.getStatus() == EmployeeStatus.INACTIVE) {
            throw new BusinessException("Inactive employees cannot request leave");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new BusinessException("End date must be on or after start date");
        }
        if (request.startDate().isBefore(LocalDate.now(clock))) {
            throw new BusinessException("Leave cannot start in the past");
        }
        LeaveRequest leave = new LeaveRequest(employee, request.type(), request.startDate(), request.endDate(),
                request.reason().trim());
        if (leave.getDays() > MAX_DAYS) {
            throw new BusinessException("A single leave request cannot exceed " + MAX_DAYS + " days");
        }
        if (leaveRepository.existsOverlapping(employeeId, request.startDate(), request.endDate(), ACTIVE_STATES)) {
            throw BusinessException.conflict("This request overlaps another pending or approved leave");
        }
        return LeaveResponse.from(leaveRepository.save(leave));
    }

    @Transactional(readOnly = true)
    public PageResponse<LeaveResponse> search(Long employeeId, LeaveStatus status, Pageable pageable) {
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
        return PageResponse.of(leaveRepository.search(employeeId, status, page), LeaveResponse::from);
    }

    @Transactional(readOnly = true)
    public LeaveResponse findById(Long id) {
        return LeaveResponse.from(get(id));
    }

    @Transactional
    public LeaveResponse approve(Long id, Long reviewerUserId, String comment) {
        return decide(id, reviewerUserId, LeaveStatus.APPROVED, comment);
    }

    @Transactional
    public LeaveResponse reject(Long id, Long reviewerUserId, String comment) {
        return decide(id, reviewerUserId, LeaveStatus.REJECTED, comment);
    }

    /** Employees may withdraw their own request while it is still pending. */
    @Transactional
    public LeaveResponse cancel(Long id, Long employeeId) {
        LeaveRequest leave = get(id);
        if (!leave.getEmployee().getId().equals(employeeId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "You can only cancel your own leave requests");
        }
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw BusinessException.conflict("Only pending requests can be cancelled (current status: " + leave.getStatus() + ")");
        }
        leave.setStatus(LeaveStatus.CANCELLED);
        return LeaveResponse.from(leave);
    }

    private LeaveResponse decide(Long id, Long reviewerUserId, LeaveStatus decision, String comment) {
        LeaveRequest leave = get(id);
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw BusinessException.conflict("Leave request is already " + leave.getStatus());
        }
        User reviewer = userRepository.findById(reviewerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", reviewerUserId));
        if (reviewer.getEmployee() != null && Objects.equals(reviewer.getEmployee().getId(), leave.getEmployee().getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "You cannot review your own leave request");
        }
        leave.review(decision, reviewer, comment);
        return LeaveResponse.from(leave);
    }

    private LeaveRequest get(Long id) {
        return leaveRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Leave request", id));
    }
}
