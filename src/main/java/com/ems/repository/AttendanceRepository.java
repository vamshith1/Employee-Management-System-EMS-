package com.ems.repository;

import com.ems.domain.Attendance;
import com.ems.domain.AttendanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import jakarta.persistence.criteria.Predicate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long>, JpaSpecificationExecutor<Attendance> {

    Optional<Attendance> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

    boolean existsByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

    /**
     * Search with optional filters. Only the filters that are present become SQL conditions:
     * PostgreSQL rejects "? is null" checks on untyped null parameters (e.g. dates), which the
     * classic "(:p is null or col = :p)" JPQL trick relies on.
     */
    default Page<Attendance> search(Long employeeId, LocalDate from, LocalDate to, AttendanceStatus status,
                                    Pageable pageable) {
        Specification<Attendance> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (employeeId != null) p.add(cb.equal(root.get("employee").get("id"), employeeId));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("workDate"), from));
            if (to != null) p.add(cb.lessThanOrEqualTo(root.get("workDate"), to));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return findAll(spec, pageable);
    }

    @Override
    @EntityGraph(attributePaths = "employee")
    Page<Attendance> findAll(Specification<Attendance> spec, Pageable pageable);

    long countByWorkDateAndStatusIn(LocalDate workDate, Collection<AttendanceStatus> statuses);

    long countByWorkDateAndCheckOutIsNotNull(LocalDate workDate);

    void deleteByEmployeeId(Long employeeId);
}
