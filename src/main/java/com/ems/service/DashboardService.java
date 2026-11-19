package com.ems.service;

import com.ems.domain.AttendanceStatus;
import com.ems.domain.EmployeeStatus;
import com.ems.domain.LeaveStatus;
import com.ems.dto.DashboardResponse;
import com.ems.dto.DashboardResponse.TodayAttendance;
import com.ems.repository.AttendanceRepository;
import com.ems.repository.DepartmentRepository;
import com.ems.repository.EmployeeRepository;
import com.ems.repository.LeaveRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;

@Service
public class DashboardService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRepository;
    private final Clock clock;

    public DashboardService(EmployeeRepository employeeRepository, DepartmentRepository departmentRepository,
                            AttendanceRepository attendanceRepository, LeaveRequestRepository leaveRepository,
                            Clock clock) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.attendanceRepository = attendanceRepository;
        this.leaveRepository = leaveRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse summary() {
        LocalDate today = LocalDate.now(clock);
        long workforce = employeeRepository.countByStatusNot(EmployeeStatus.INACTIVE);
        long onTime = attendanceRepository.countByWorkDateAndStatusIn(today, EnumSet.of(AttendanceStatus.PRESENT));
        long late = attendanceRepository.countByWorkDateAndStatusIn(today, EnumSet.of(AttendanceStatus.LATE));
        long halfDay = attendanceRepository.countByWorkDateAndStatusIn(today, EnumSet.of(AttendanceStatus.HALF_DAY));
        long checkedOut = attendanceRepository.countByWorkDateAndCheckOutIsNotNull(today);
        long onLeave = leaveRepository.countEmployeesOnLeave(today);
        long present = onTime + late + halfDay;

        return new DashboardResponse(
                today,
                employeeRepository.count(),
                employeeRepository.countByStatus(EmployeeStatus.ACTIVE),
                departmentRepository.count(),
                departmentRepository.headcountByDepartment(),
                new TodayAttendance(present, late, checkedOut, Math.max(0, workforce - present - onLeave)),
                leaveRepository.countByStatus(LeaveStatus.PENDING),
                onLeave);
    }
}
