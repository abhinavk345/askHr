package com.intech.ai.service;

import com.intech.ai.modal.Employee;
import com.intech.ai.modal.EmployeeAuth;
import com.intech.ai.repository.EmployeeAuthRepository;
import com.intech.ai.repository.EmployeeRepository;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Service
public class EmployeeService {

    private final JdbcChatMemoryRepository chatMemoryRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeAuthRepository employeeAuthRepository;

    public EmployeeService(JdbcChatMemoryRepository chatMemoryRepository,
                           EmployeeRepository employeeRepository,
                           EmployeeAuthRepository employeeAuthRepository) {
        this.chatMemoryRepository = chatMemoryRepository;
        this.employeeRepository = employeeRepository;
        this.employeeAuthRepository = employeeAuthRepository;
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


    public void clearChatMemory(String employeeId) {
        chatMemoryRepository.deleteByConversationId(employeeId);
    }

    public Employee validateLogin(String employeeId, String password) {

        Optional<EmployeeAuth> authOpt =
                employeeAuthRepository.findByEmployeeId(employeeId);

        if (authOpt.isEmpty()) {
            return null;
        }

        EmployeeAuth auth = authOpt.get();

        // check account enabled
        if (!auth.isEnabled()) {
            return null;
        }

        // plain password comparison (NOT secure)
        if (!password.equals(auth.getPassword())) {
            return null;
        }

        return employeeRepository
                .findByEmployeeId(employeeId)
                .orElse(null);
    }
}
