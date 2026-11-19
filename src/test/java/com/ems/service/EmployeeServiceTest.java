package com.ems.service;

import com.ems.domain.Department;
import com.ems.domain.Employee;
import com.ems.domain.EmployeeStatus;
import com.ems.dto.EmployeeDtos.EmployeeRequest;
import com.ems.dto.EmployeeDtos.EmployeeSearchCriteria;
import com.ems.exception.BusinessException;
import com.ems.exception.ResourceNotFoundException;
import com.ems.repository.AttendanceRepository;
import com.ems.repository.EmployeeRepository;
import com.ems.repository.LeaveRequestRepository;
import com.ems.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    EmployeeRepository employeeRepository;
    @Mock
    DepartmentService departmentService;
    @Mock
    UserRepository userRepository;
    @Mock
    AttendanceRepository attendanceRepository;
    @Mock
    LeaveRequestRepository leaveRequestRepository;

    @InjectMocks
    EmployeeService service;

    Department engineering;

    @BeforeEach
    void setUp() {
        engineering = new Department("Engineering", null);
    }

    private static EmployeeRequest request(String email) {
        return new EmployeeRequest(" Asha ", "Rao", email, null, "Developer", new BigDecimal("50000"),
                LocalDate.of(2024, 1, 15), null, 1L);
    }

    @Test
    void createNormalisesInputAndDefaultsToActive() {
        when(employeeRepository.existsByEmailIgnoreCase("Asha.Rao@Example.com")).thenReturn(false);
        when(departmentService.get(1L)).thenReturn(engineering);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.create(request("Asha.Rao@Example.com"));

        assertThat(response.firstName()).isEqualTo("Asha");
        assertThat(response.email()).isEqualTo("asha.rao@example.com");
        assertThat(response.status()).isEqualTo(EmployeeStatus.ACTIVE);
        assertThat(response.departmentName()).isEqualTo("Engineering");
    }

    @Test
    void createRejectsDuplicateEmail() {
        when(employeeRepository.existsByEmailIgnoreCase("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("dup@example.com")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void updateUnknownEmployeeIsNotFound() {
        when(employeeRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(42L, request("x@example.com")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchRejectsUnknownSortProperty() {
        var criteria = new EmployeeSearchCriteria(null, null, null, null, null, null);

        assertThatThrownBy(() -> service.search(criteria, PageRequest.of(0, 10, Sort.by("password"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot sort by 'password'");
    }
}
