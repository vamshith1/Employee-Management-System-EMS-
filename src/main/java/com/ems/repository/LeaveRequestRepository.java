package com.ems.repository;

import com.ems.domain.LeaveRequest;
import com.ems.domain.LeaveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    @EntityGraph(attributePaths = {"employee", "reviewedBy"})
    @Query("""
            select l from LeaveRequest l
            where (:employeeId is null or l.employee.id = :employeeId)
              and (:status is null or l.status = :status)
            """)
    Page<LeaveRequest> search(@Param("employeeId") Long employeeId,
                              @Param("status") LeaveStatus status,
                              Pageable pageable);

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

    @Query("""
            select count(distinct l.employee.id) from LeaveRequest l
            where l.status = com.ems.domain.LeaveStatus.APPROVED
              and l.startDate <= :day and l.endDate >= :day
            """)
    long countEmployeesOnLeave(@Param("day") LocalDate day);

    void deleteByEmployeeId(Long employeeId);
}
