package com.intech.ai.service;

import com.intech.ai.modal.Employee;
import com.intech.ai.repository.EmployeeRepository;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EmployeeService {


    private final JdbcChatMemoryRepository chatMemoryRepository;

    private final EmployeeRepository repository;

    public EmployeeService(JdbcChatMemoryRepository chatMemoryRepository, EmployeeRepository repository) {
        this.chatMemoryRepository = chatMemoryRepository;
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
