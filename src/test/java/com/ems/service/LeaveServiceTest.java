package com.ems.service;

import com.ems.domain.*;
import com.ems.dto.LeaveDtos.LeaveRequestCreate;
import com.ems.dto.LeaveDtos.LeaveResponse;
import com.ems.exception.BusinessException;
import com.ems.repository.LeaveRequestRepository;
import com.ems.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 10);

    @Mock
    LeaveRequestRepository leaveRepository;
    @Mock
    EmployeeService employeeService;
    @Mock
    UserRepository userRepository;

    LeaveService service;
    Employee employee;
    User hrUser;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZONE).toInstant(), ZONE);
        service = new LeaveService(leaveRepository, employeeService, userRepository, clock);

        employee = employee(7L, "Arjun", "Mehta");
        Employee hrEmployee = employee(2L, "Priya", "Sharma");
        hrUser = new User("hr", "x", Role.HR, hrEmployee);
        ReflectionTestUtils.setField(hrUser, "id", 20L);
    }

    private static Employee employee(Long id, String first, String last) {
        Employee e = new Employee();
        ReflectionTestUtils.setField(e, "id", id);
        e.setFirstName(first);
        e.setLastName(last);
        e.setStatus(EmployeeStatus.ACTIVE);
        e.setDepartment(new Department("Engineering", null));
        return e;
    }

    @Test
    void submitCreatesPendingRequest() {
        when(employeeService.get(7L)).thenReturn(employee);
        when(leaveRepository.existsOverlapping(eq(7L), any(), any(), anyCollection())).thenReturn(false);
        when(leaveRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        LeaveResponse response = service.submit(7L,
                new LeaveRequestCreate(LeaveType.ANNUAL, TODAY.plusDays(5), TODAY.plusDays(7), "  Vacation "));

        assertThat(response.status()).isEqualTo(LeaveStatus.PENDING);
        assertThat(response.days()).isEqualTo(3);
        assertThat(response.reason()).isEqualTo("Vacation");
    }

    @Test
    void submitRejectsPastStartDate() {
        when(employeeService.get(7L)).thenReturn(employee);

        assertThatThrownBy(() -> service.submit(7L,
                new LeaveRequestCreate(LeaveType.SICK, TODAY.minusDays(1), TODAY, "Flu")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("past");
    }

    @Test
    void submitRejectsOverlap() {
        when(employeeService.get(7L)).thenReturn(employee);
        when(leaveRepository.existsOverlapping(eq(7L), any(), any(), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> service.submit(7L,
                new LeaveRequestCreate(LeaveType.CASUAL, TODAY.plusDays(1), TODAY.plusDays(2), "Errands")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(leaveRepository, never()).save(any());
    }

    @Test
    void approveRecordsReviewer() {
        LeaveRequest leave = pending();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(userRepository.findById(20L)).thenReturn(Optional.of(hrUser));

        LeaveResponse response = service.approve(1L, 20L, "Enjoy");

        assertThat(response.status()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(response.reviewedBy()).isEqualTo("hr");
        assertThat(response.reviewComment()).isEqualTo("Enjoy");
        assertThat(response.reviewedAt()).isNotNull();
    }

    @Test
    void cannotDecideTwice() {
        LeaveRequest leave = pending();
        leave.setStatus(LeaveStatus.REJECTED);
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        assertThatThrownBy(() -> service.approve(1L, 20L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already REJECTED");
    }

    @Test
    void reviewerCannotApproveOwnRequest() {
        LeaveRequest ownLeave = new LeaveRequest(hrUser.getEmployee(), LeaveType.ANNUAL,
                TODAY.plusDays(1), TODAY.plusDays(2), "Mine");
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(ownLeave));
        when(userRepository.findById(20L)).thenReturn(Optional.of(hrUser));

        assertThatThrownBy(() -> service.approve(1L, 20L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("own leave");
    }

    @Test
    void employeeCanOnlyCancelOwnPendingRequest() {
        LeaveRequest leave = pending();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        assertThatThrownBy(() -> service.cancel(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("your own");

        assertThat(service.cancel(1L, 7L).status()).isEqualTo(LeaveStatus.CANCELLED);
    }

    private LeaveRequest pending() {
        return new LeaveRequest(employee, LeaveType.ANNUAL, TODAY.plusDays(3), TODAY.plusDays(4), "Trip");
    }
}
