package com.ems.service;

import com.ems.domain.Attendance;
import com.ems.domain.AttendanceStatus;
import com.ems.domain.Department;
import com.ems.domain.Employee;
import com.ems.domain.EmployeeStatus;
import com.ems.dto.AttendanceDtos.AttendanceResponse;
import com.ems.exception.BusinessException;
import com.ems.repository.AttendanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);

    @Mock
    AttendanceRepository attendanceRepository;
    @Mock
    EmployeeService employeeService;

    Employee employee;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        ReflectionTestUtils.setField(employee, "id", 7L);
        employee.setFirstName("Arjun");
        employee.setLastName("Mehta");
        employee.setDepartment(new Department("Engineering", null));
        employee.setStatus(EmployeeStatus.ACTIVE);
    }

    private AttendanceService serviceAt(int hour, int minute) {
        Clock clock = Clock.fixed(DAY.atTime(hour, minute).atZone(ZONE).toInstant(), ZONE);
        return new AttendanceService(attendanceRepository, employeeService, clock, "09:30");
    }

    @Test
    void checkInBeforeCutoffIsPresent() {
        when(employeeService.get(7L)).thenReturn(employee);
        when(attendanceRepository.existsByEmployeeIdAndWorkDate(7L, DAY)).thenReturn(false);
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));

        AttendanceResponse response = serviceAt(9, 5).checkIn(7L, "on time");

        assertThat(response.status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(response.workDate()).isEqualTo(DAY);
        assertThat(response.checkIn()).isEqualTo(DAY.atTime(9, 5));
        assertThat(response.checkOut()).isNull();
    }

    @Test
    void checkInAfterCutoffIsLate() {
        when(employeeService.get(7L)).thenReturn(employee);
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(serviceAt(10, 15).checkIn(7L, null).status()).isEqualTo(AttendanceStatus.LATE);
    }

    @Test
    void secondCheckInSameDayIsConflict() {
        when(employeeService.get(7L)).thenReturn(employee);
        when(attendanceRepository.existsByEmployeeIdAndWorkDate(7L, DAY)).thenReturn(true);

        assertThatThrownBy(() -> serviceAt(9, 0).checkIn(7L, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void inactiveEmployeeCannotCheckIn() {
        employee.setStatus(EmployeeStatus.INACTIVE);
        when(employeeService.get(7L)).thenReturn(employee);

        assertThatThrownBy(() -> serviceAt(9, 0).checkIn(7L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Inactive");
    }

    @Test
    void checkOutWithoutCheckInFails() {
        when(attendanceRepository.findByEmployeeIdAndWorkDate(7L, DAY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceAt(18, 0).checkOut(7L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not checked in");
    }

    @Test
    void checkOutRecordsTimeAndHoursWorked() {
        Attendance attendance = new Attendance(employee, DAY, AttendanceStatus.PRESENT);
        attendance.setCheckIn(DAY.atTime(9, 0));
        when(attendanceRepository.findByEmployeeIdAndWorkDate(7L, DAY)).thenReturn(Optional.of(attendance));

        AttendanceResponse response = serviceAt(17, 30).checkOut(7L, null);

        assertThat(response.checkOut()).isEqualTo(DAY.atTime(17, 30));
        assertThat(response.hoursWorked()).isEqualTo(8.5);
    }

    @Test
    void doubleCheckOutIsConflict() {
        Attendance attendance = new Attendance(employee, DAY, AttendanceStatus.PRESENT);
        attendance.setCheckIn(DAY.atTime(9, 0));
        attendance.setCheckOut(DAY.atTime(17, 0));
        when(attendanceRepository.findByEmployeeIdAndWorkDate(7L, DAY)).thenReturn(Optional.of(attendance));

        assertThatThrownBy(() -> serviceAt(18, 0).checkOut(7L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already checked out");
    }
}
