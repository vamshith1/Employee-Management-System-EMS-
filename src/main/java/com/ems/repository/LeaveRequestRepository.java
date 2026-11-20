package com.ems.repository;

import com.ems.domain.LeaveRequest;
import com.ems.domain.LeaveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long>, JpaSpecificationExecutor<LeaveRequest> {

    /** Optional filters become SQL conditions only when present (portable across H2, MySQL, PostgreSQL). */
    default Page<LeaveRequest> search(Long employeeId, LeaveStatus status, Pageable pageable) {
        Specification<LeaveRequest> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (employeeId != null) p.add(cb.equal(root.get("employee").get("id"), employeeId));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return findAll(spec, pageable);
    }

    @Override
    @EntityGraph(attributePaths = {"employee", "reviewedBy"})
    Page<LeaveRequest> findAll(Specification<LeaveRequest> spec, Pageable pageable);

    /** True if the employee already has a request in one of the given states overlapping [start, end]. */
    @Query("""
            select count(l) > 0 from LeaveRequest l
            where l.employee.id = :employeeId
              and l.status in :statuses
              and l.startDate <= :end and l.endDate >= :start
            """)
    boolean existsOverlapping(@Param("employeeId") Long employeeId,
                              @Param("start") LocalDate start,
                              @Param("end") LocalDate end,
                              @Param("statuses") Collection<LeaveStatus> statuses);

    long countByStatus(LeaveStatus status);

    boolean existsByReason(String reason);

    @Query("""
            select count(distinct l.employee.id) from LeaveRequest l
            where l.status = com.ems.domain.LeaveStatus.APPROVED
              and l.startDate <= :day and l.endDate >= :day
            """)
    long countEmployeesOnLeave(@Param("day") LocalDate day);

    void deleteByEmployeeId(Long employeeId);
}
