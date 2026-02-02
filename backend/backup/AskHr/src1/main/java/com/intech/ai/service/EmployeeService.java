package com.intech.ai.service;

import com.intech.ai.modal.Employee;
import com.intech.ai.repository.EmployeeRepository;
import org.springframework.stereotype.Service;

@Service
public class EmployeeService {

    private final EmployeeRepository repository;

    public EmployeeService(EmployeeRepository repository) {
        this.repository = repository;
    }

    public boolean employeeExists(String employeeId) {
        return repository.existsByEmployeeId(employeeId);
    }

    public Employee validateLogin(String employeeId, String password) {
        return repository.findByEmployeeId(employeeId)
                .filter(emp -> emp.getPassword().equals(password))
                .orElse(null);
    }
}
