package com.ems.service;

import com.ems.domain.Employee;
import com.ems.domain.User;
import com.ems.dto.UserDtos.*;
import com.ems.exception.BusinessException;
import com.ems.exception.ResourceNotFoundException;
import com.ems.repository.EmployeeRepository;
import com.ems.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, EmployeeRepository employeeRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll(Sort.by("username")).stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return UserResponse.from(get(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw BusinessException.conflict("Username '" + request.username() + "' is already taken");
        }
        Employee employee = null;
        if (request.employeeId() != null) {
            if (userRepository.existsByEmployeeId(request.employeeId())) {
                throw BusinessException.conflict("Employee " + request.employeeId() + " already has a user account");
            }
            employee = findEmployee(request.employeeId());
        }
        User user = new User(request.username(), passwordEncoder.encode(request.password()), request.role(), employee);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request, Long actingUserId) {
        User user = get(id);
        if (Objects.equals(id, actingUserId) && (!request.enabled() || request.role() != user.getRole())) {
            throw new BusinessException("You cannot disable your own account or change your own role");
        }
        user.setRole(request.role());
        user.setEnabled(request.enabled());
        if (request.employeeId() == null) {
            user.setEmployee(null);
        } else if (user.getEmployee() == null || !user.getEmployee().getId().equals(request.employeeId())) {
            if (userRepository.existsByEmployeeIdAndIdNot(request.employeeId(), id)) {
                throw BusinessException.conflict("Employee " + request.employeeId() + " already has a user account");
            }
            user.setEmployee(findEmployee(request.employeeId()));
        }
        return UserResponse.from(user);
    }

    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest request) {
        get(id).setPassword(passwordEncoder.encode(request.password()));
    }

    @Transactional
    public void delete(Long id, Long actingUserId) {
        if (Objects.equals(id, actingUserId)) {
            throw new BusinessException("You cannot delete your own account");
        }
        userRepository.delete(get(id));
        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw BusinessException.conflict("User has reviewed leave requests and cannot be deleted; disable the account instead");
        }
    }

    private User get(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private Employee findEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));
    }
}
