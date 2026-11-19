package com.ems.repository;

import com.ems.domain.Department;
import com.ems.dto.DepartmentHeadcount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    /** Headcount of non-inactive employees per department, including departments with nobody in them. */
    @Query("""
            select new com.ems.dto.DepartmentHeadcount(d.id, d.name, count(e.id))
            from Department d
            left join Employee e on e.department = d and e.status <> com.ems.domain.EmployeeStatus.INACTIVE
            group by d.id, d.name
            order by d.name
            """)
    List<DepartmentHeadcount> headcountByDepartment();
}
