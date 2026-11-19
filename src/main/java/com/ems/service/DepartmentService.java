package com.ems.service;

import com.ems.domain.Department;
import com.ems.dto.DepartmentDtos.DepartmentRequest;
import com.ems.dto.DepartmentDtos.DepartmentResponse;
import com.ems.exception.BusinessException;
import com.ems.exception.ResourceNotFoundException;
import com.ems.repository.DepartmentRepository;
import com.ems.repository.EmployeeRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;

    public DepartmentService(DepartmentRepository departmentRepository, EmployeeRepository employeeRepository) {
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> findAll() {
        return departmentRepository.findAll(Sort.by("name")).stream().map(DepartmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DepartmentResponse findById(Long id) {
        return DepartmentResponse.from(get(id));
    }

    @Transactional
    public DepartmentResponse create(DepartmentRequest request) {
        String name = request.name().trim();
        if (departmentRepository.existsByNameIgnoreCase(name)) {
            throw BusinessException.conflict("Department '" + name + "' already exists");
        }
        return DepartmentResponse.from(departmentRepository.save(new Department(name, request.description())));
    }

    @Transactional
    public DepartmentResponse update(Long id, DepartmentRequest request) {
        Department department = get(id);
        String name = request.name().trim();
        departmentRepository.findByNameIgnoreCase(name)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw BusinessException.conflict("Department '" + name + "' already exists");
                });
        department.setName(name);
        department.setDescription(request.description());
        return DepartmentResponse.from(department);
    }

    @Transactional
    public void delete(Long id) {
        Department department = get(id);
        if (employeeRepository.existsByDepartmentId(id)) {
            throw BusinessException.conflict("Cannot delete a department that still has employees; reassign them first");
        }
        departmentRepository.delete(department);
    }

    Department get(Long id) {
        return departmentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Department", id));
    }
}
