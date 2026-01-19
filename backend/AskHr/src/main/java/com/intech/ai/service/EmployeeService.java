package com.intech.ai.service;

import com.intech.ai.modal.Employee;
import com.intech.ai.repository.EmployeeRepository;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.stereotype.Service;


@Service
public class EmployeeService {

    private final JdbcChatMemoryRepository chatMemoryRepository;
    private final EmployeeRepository employeeRepository;

    public EmployeeService(JdbcChatMemoryRepository chatMemoryRepository,
                           EmployeeRepository employeeRepository) {
        this.chatMemoryRepository = chatMemoryRepository;
        this.employeeRepository = employeeRepository;
    }

    /* ================= Employee Profile ================= */

    public boolean employeeExists(String employeeId) {
        return employeeRepository.existsByEmployeeId(employeeId);
    }

    public Employee getEmployeeByEmployeeId(String employeeId) {
        return employeeRepository.findByEmployeeId(employeeId)
                .orElseThrow(() ->
                        new RuntimeException("Employee not found: " + employeeId));
    }

    public String getManagerName(String employeeId) {
        return employeeRepository.findByEmployeeId(employeeId)
                .map(Employee::getManagerEmployeeId)
                .orElse("Not Assigned");
    }

    /* ================= Chat Memory ================= */

    public void clearChatMemory(String employeeId) {
        chatMemoryRepository.deleteByConversationId(employeeId);
    }
}
