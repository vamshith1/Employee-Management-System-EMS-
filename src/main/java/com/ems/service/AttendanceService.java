package com.ems.service;

import com.ems.domain.Attendance;
import com.ems.domain.AttendanceStatus;
import com.ems.domain.Employee;
import com.ems.domain.EmployeeStatus;
import com.ems.dto.AttendanceDtos.AttendanceRequest;
import com.ems.dto.AttendanceDtos.AttendanceResponse;
import com.ems.dto.PageResponse;
import com.ems.exception.BusinessException;
import com.ems.exception.ResourceNotFoundException;
import com.ems.repository.AttendanceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

@Service
public class AttendanceService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "workDate").and(Sort.by("id"));

    private final AttendanceRepository attendanceRepository;
    private final EmployeeService employeeService;
    private final Clock clock;
    private final LocalTime lateAfter;

    public AttendanceService(AttendanceRepository attendanceRepository, EmployeeService employeeService, Clock clock,
                             @Value("${app.attendance.late-after:09:30}") String lateAfter) {
        this.attendanceRepository = attendanceRepository;
        this.employeeService = employeeService;
        this.clock = clock;
        this.lateAfter = LocalTime.parse(lateAfter);
    }

    @Transactional
    public AttendanceResponse checkIn(Long employeeId, String notes) {
        Employee employee = employeeService.get(employeeId);
        if (employee.getStatus() == EmployeeStatus.INACTIVE) {
            throw new BusinessException("Inactive employees cannot check in");
        }
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        if (attendanceRepository.existsByEmployeeIdAndWorkDate(employeeId, now.toLocalDate())) {
            throw BusinessException.conflict("You have already checked in today");
        }
        AttendanceStatus status = now.toLocalTime().isAfter(lateAfter) ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;
        Attendance attendance = new Attendance(employee, now.toLocalDate(), status);
        attendance.setCheckIn(now);
        attendance.setNotes(notes);
        return AttendanceResponse.from(attendanceRepository.save(attendance));
    }

    @Transactional
    public AttendanceResponse checkOut(Long employeeId, String notes) {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        Attendance attendance = attendanceRepository.findByEmployeeIdAndWorkDate(employeeId, now.toLocalDate())
                .orElseThrow(() -> new BusinessException("You have not checked in today"));
        if (attendance.getCheckIn() == null) {
            throw new BusinessException("Today's attendance has no check-in time; ask HR to correct it");
        }
        if (attendance.getCheckOut() != null) {
            throw BusinessException.conflict("You have already checked out today");
        }
        attendance.setCheckOut(now);
        if (notes != null && !notes.isBlank()) {
            attendance.setNotes(notes);
        }
        return AttendanceResponse.from(attendance);
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendanceResponse> search(Long employeeId, LocalDate from, LocalDate to,
                                                   AttendanceStatus status, Pageable pageable) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new BusinessException("'to' must be on or after 'from'");
        }
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
        return PageResponse.of(attendanceRepository.search(employeeId, from, to, status, page),
                AttendanceResponse::from);
    }

    @Transactional
    public AttendanceResponse create(AttendanceRequest request) {
        Employee employee = employeeService.get(request.employeeId());
        if (attendanceRepository.existsByEmployeeIdAndWorkDate(employee.getId(), request.workDate())) {
            throw BusinessException.conflict("Attendance for this employee on " + request.workDate() + " already exists");
        }
        Attendance attendance = new Attendance(employee, request.workDate(), request.status());
        applyTimes(attendance, request);
        return AttendanceResponse.from(attendanceRepository.save(attendance));
    }

    @Transactional
    public AttendanceResponse update(Long id, AttendanceRequest request) {
        Attendance attendance = get(id);
        if (!attendance.getEmployee().getId().equals(request.employeeId())) {
            throw new BusinessException("An attendance record cannot be moved to a different employee");
        }
        if (!attendance.getWorkDate().equals(request.workDate())
                && attendanceRepository.existsByEmployeeIdAndWorkDate(request.employeeId(), request.workDate())) {
            throw BusinessException.conflict("Attendance for this employee on " + request.workDate() + " already exists");
        }
        attendance.setWorkDate(request.workDate());
        attendance.setStatus(request.status());
        applyTimes(attendance, request);
        return AttendanceResponse.from(attendance);
    }

    @Transactional
    public void delete(Long id) {
        attendanceRepository.delete(get(id));
    }

    private void applyTimes(Attendance attendance, AttendanceRequest request) {
        if (request.checkIn() != null && !request.checkIn().toLocalDate().equals(request.workDate())) {
            throw new BusinessException("Check-in must be on the work date");
        }
        attendance.setCheckIn(request.checkIn());
        attendance.setCheckOut(request.checkOut());
        attendance.setNotes(request.notes());
    }

    private Attendance get(Long id) {
        return attendanceRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Attendance", id));
    }
}
