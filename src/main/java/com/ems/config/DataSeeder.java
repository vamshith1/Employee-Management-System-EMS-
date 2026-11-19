package com.ems.config;

import com.ems.domain.*;
import com.ems.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * Inserts demo departments, employees, accounts, attendance and leave on an empty database.
 * Disable with {@code SEED_DATA=false}. The demo passwords are documented in the README and
 * must be changed (or seeding disabled) outside local development.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    public static final String ADMIN_USERNAME = "admin";
    public static final String ADMIN_PASSWORD = "Admin@123";
    public static final String HR_USERNAME = "hr";
    public static final String HR_PASSWORD = "Hr@12345";
    public static final String EMPLOYEE_USERNAME = "employee";
    public static final String EMPLOYEE_PASSWORD = "Employee@123";

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DataSeeder(UserRepository userRepository, DepartmentRepository departmentRepository,
                      EmployeeRepository employeeRepository, AttendanceRepository attendanceRepository,
                      LeaveRequestRepository leaveRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
        this.leaveRepository = leaveRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            log.info("Database already has users; skipping demo seed data");
            return;
        }
        LocalDate today = LocalDate.now(clock);

        Department engineering = departmentRepository.save(new Department("Engineering", "Builds and runs the product"));
        Department hrDept = departmentRepository.save(new Department("Human Resources", "People operations and hiring"));
        Department sales = departmentRepository.save(new Department("Sales", "Customer acquisition and accounts"));
        Department finance = departmentRepository.save(new Department("Finance", "Accounting, payroll and budgets"));
        departmentRepository.save(new Department("Marketing", "Brand and demand generation"));

        Employee hrManager = employee("Priya", "Sharma", "priya.sharma@ems.local", "HR Manager", "85000", today.minusYears(4), hrDept);
        Employee dev = employee("Arjun", "Mehta", "arjun.mehta@ems.local", "Software Engineer", "72000", today.minusYears(2), engineering);
        List<Employee> others = List.of(
                employee("Neha", "Gupta", "neha.gupta@ems.local", "Senior Software Engineer", "98000", today.minusYears(5), engineering),
                employee("Rahul", "Verma", "rahul.verma@ems.local", "QA Engineer", "61000", today.minusMonths(14), engineering),
                employee("Ananya", "Iyer", "ananya.iyer@ems.local", "Account Executive", "58000", today.minusMonths(9), sales),
                employee("Vikram", "Singh", "vikram.singh@ems.local", "Sales Manager", "90000", today.minusYears(6), sales),
                employee("Kavya", "Nair", "kavya.nair@ems.local", "Accountant", "64000", today.minusYears(3), finance),
                employee("Rohan", "Das", "rohan.das@ems.local", "Recruiter", "52000", today.minusMonths(5), hrDept));
        Employee former = employee("Sanjay", "Kapoor", "sanjay.kapoor@ems.local", "Financial Analyst", "70000", today.minusYears(7), finance);
        former.setStatus(EmployeeStatus.INACTIVE);

        userRepository.save(new User(ADMIN_USERNAME, passwordEncoder.encode(ADMIN_PASSWORD), Role.ADMIN, null));
        userRepository.save(new User(HR_USERNAME, passwordEncoder.encode(HR_PASSWORD), Role.HR, hrManager));
        userRepository.save(new User(EMPLOYEE_USERNAME, passwordEncoder.encode(EMPLOYEE_PASSWORD), Role.EMPLOYEE, dev));

        // Last 10 weekdays of attendance (never today, so the demo employee can check in).
        int seeded = 0;
        for (LocalDate day = today.minusDays(1); seeded < 10; day = day.minusDays(1)) {
            if (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            int i = 0;
            for (Employee e : concat(hrManager, dev, others)) {
                boolean late = (seeded + i) % 7 == 0;
                Attendance a = new Attendance(e, day, late ? AttendanceStatus.LATE : AttendanceStatus.PRESENT);
                a.setCheckIn(day.atTime(late ? 10 : 9, (i * 7) % 30));
                a.setCheckOut(day.atTime(18, (i * 11) % 45));
                attendanceRepository.save(a);
                i++;
            }
            seeded++;
        }

        leaveRepository.save(new LeaveRequest(dev, LeaveType.ANNUAL, today.plusDays(14), today.plusDays(18), "Family trip"));
        leaveRepository.save(new LeaveRequest(others.get(2), LeaveType.SICK, today.plusDays(2), today.plusDays(3), "Medical appointment"));
        LeaveRequest past = leaveRepository.save(new LeaveRequest(others.get(0), LeaveType.CASUAL,
                today.minusDays(20), today.minusDays(19), "Personal errands"));
        past.setStatus(LeaveStatus.APPROVED);

        log.info("Seeded demo data. Logins: {}/{} (ADMIN), {}/{} (HR), {}/{} (EMPLOYEE)",
                ADMIN_USERNAME, ADMIN_PASSWORD, HR_USERNAME, HR_PASSWORD, EMPLOYEE_USERNAME, EMPLOYEE_PASSWORD);
    }

    private Employee employee(String first, String last, String email, String title, String salary,
                              LocalDate hired, Department dept) {
        Employee e = new Employee();
        e.setFirstName(first);
        e.setLastName(last);
        e.setEmail(email);
        e.setJobTitle(title);
        e.setSalary(new BigDecimal(salary));
        e.setHireDate(hired);
        e.setDepartment(dept);
        e.setStatus(EmployeeStatus.ACTIVE);
        return employeeRepository.save(e);
    }

    private static List<Employee> concat(Employee a, Employee b, List<Employee> rest) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(a, b), rest.stream()).toList();
    }
}
