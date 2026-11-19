package com.ems.repository;

import com.ems.domain.Attendance;
import com.ems.domain.AttendanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

    boolean existsByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

    @EntityGraph(attributePaths = "employee")
    @Query("""
            select a from Attendance a
            where (:employeeId is null or a.employee.id = :employeeId)
              and (:from is null or a.workDate >= :from)
              and (:to is null or a.workDate <= :to)
              and (:status is null or a.status = :status)
            """)
    Page<Attendance> search(@Param("employeeId") Long employeeId,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to,
                            @Param("status") AttendanceStatus status,
                            Pageable pageable);

    long countByWorkDateAndStatusIn(LocalDate workDate, Collection<AttendanceStatus> statuses);

    long countByWorkDateAndCheckOutIsNotNull(LocalDate workDate);

    void deleteByEmployeeId(Long employeeId);
}
