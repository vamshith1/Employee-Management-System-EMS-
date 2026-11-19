package com.ems.service;

import com.ems.domain.Employee;
import com.ems.dto.EmployeeDtos.EmployeeSearchCriteria;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds a dynamic JPA query from optional search filters. */
public final class EmployeeSpecifications {

    private EmployeeSpecifications() {
    }

    public static Specification<Employee> matching(EmployeeSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (hasText(c.q())) {
                String like = "%" + c.q().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), like),
                        cb.like(cb.lower(root.get("lastName")), like),
                        cb.like(cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"))), like),
                        cb.like(cb.lower(root.get("email")), like)));
            }
            if (c.departmentId() != null) {
                predicates.add(cb.equal(root.get("department").get("id"), c.departmentId()));
            }
            if (c.status() != null) {
                predicates.add(cb.equal(root.get("status"), c.status()));
            }
            if (hasText(c.jobTitle())) {
                predicates.add(cb.like(cb.lower(root.get("jobTitle")),
                        "%" + c.jobTitle().trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (c.hiredFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("hireDate"), c.hiredFrom()));
            }
            if (c.hiredTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("hireDate"), c.hiredTo()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
