package com.ems.service;

import com.ems.domain.Employee;
import com.ems.domain.EmployeeStatus;
import com.ems.dto.EmployeeDtos.EmployeeRequest;
import com.ems.dto.EmployeeDtos.EmployeeResponse;
import com.ems.dto.EmployeeDtos.EmployeeSearchCriteria;
import com.ems.dto.PageResponse;
import com.ems.exception.BusinessException;
import com.ems.exception.ResourceNotFoundException;
import com.ems.repository.AttendanceRepository;
import com.ems.repository.EmployeeRepository;
import com.ems.repository.LeaveRequestRepository;
import com.ems.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Service
public class EmployeeService {

    /** API sort keys -> entity paths. Anything else is rejected with 400 instead of a SQL error. */
    private static final Map<String, String> SORTABLE = Map.of(
            "id", "id",
            "firstName", "firstName",
            "lastName", "lastName",
            "email", "email",
            "jobTitle", "jobTitle",
            "salary", "salary",
            "hireDate", "hireDate",
            "status", "status",
            "department", "department.name");

    private final EmployeeRepository employeeRepository;
    private final DepartmentService departmentService;
    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public EmployeeService(EmployeeRepository employeeRepository, DepartmentService departmentService,
                           UserRepository userRepository, AttendanceRepository attendanceRepository,
                           LeaveRequestRepository leaveRequestRepository) {
        this.employeeRepository = employeeRepository;
        this.departmentService = departmentService;
        this.userRepository = userRepository;
        this.attendanceRepository = attendanceRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> search(EmployeeSearchCriteria criteria, Pageable pageable) {
        Pageable safe = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), translateSort(pageable.getSort()));
        return PageResponse.of(employeeRepository.findAll(EmployeeSpecifications.matching(criteria), safe),
                EmployeeResponse::from);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse findById(Long id) {
        return EmployeeResponse.from(get(id));
    }

    @Transactional
    public EmployeeResponse create(EmployeeRequest request) {
        if (employeeRepository.existsByEmailIgnoreCase(request.email())) {
            throw BusinessException.conflict("An employee with email '" + request.email() + "' already exists");
        }
        Employee employee = new Employee();
        apply(employee, request);
        return EmployeeResponse.from(employeeRepository.save(employee));
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeRequest request) {
        Employee employee = get(id);
        if (employeeRepository.existsByEmailIgnoreCaseAndIdNot(request.email(), id)) {
            throw BusinessException.conflict("An employee with email '" + request.email() + "' already exists");
        }
        apply(employee, request);
        return EmployeeResponse.from(employee);
    }

    /**
     * Removes the employee together with their attendance and leave history. A linked login is
     * kept for audit purposes but unlinked and disabled.
     */
    @Transactional
    public void delete(Long id) {
        Employee employee = get(id);
        userRepository.findByEmployeeId(id).ifPresent(user -> {
            user.setEmployee(null);
            user.setEnabled(false);
        });
        attendanceRepository.deleteByEmployeeId(id);
        leaveRequestRepository.deleteByEmployeeId(id);
        employeeRepository.delete(employee);
    }

    Employee get(Long id) {
        return employeeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Employee", id));
    }

    private void apply(Employee e, EmployeeRequest r) {
        e.setFirstName(r.firstName().trim());
        e.setLastName(r.lastName().trim());
        e.setEmail(r.email().trim().toLowerCase());
        e.setPhone(r.phone());
        e.setJobTitle(r.jobTitle().trim());
        e.setSalary(r.salary());
        e.setHireDate(r.hireDate());
        e.setStatus(r.status() == null ? EmployeeStatus.ACTIVE : r.status());
        e.setDepartment(departmentService.get(r.departmentId()));
    }

    private static Sort translateSort(Sort requested) {
        if (requested.isUnsorted()) {
            return Sort.by("lastName").and(Sort.by("firstName"));
        }
        Sort result = Sort.unsorted();
        for (Sort.Order order : requested) {
            String path = SORTABLE.get(order.getProperty());
            if (path == null) {
                throw new BusinessException("Cannot sort by '" + order.getProperty() + "'. Allowed: "
                        + String.join(", ", Set.copyOf(SORTABLE.keySet()).stream().sorted().toList()));
            }
            result = result.and(Sort.by(order.getDirection(), path));
        }
        return result;
    }
}
