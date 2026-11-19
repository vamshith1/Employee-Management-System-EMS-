package com.ems.repository;

import com.ems.domain.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = "employee")
    Optional<User> findByUsername(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmployeeId(Long employeeId);

    boolean existsByEmployeeIdAndIdNot(Long employeeId, Long id);

    Optional<User> findByEmployeeId(Long employeeId);
}
