package com.intech.ai.tools;

import com.intech.ai.modal.Employee;
import com.intech.ai.service.EmployeeService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class EmployeeTools {

    private final EmployeeService employeeService;

    public EmployeeTools(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @Tool(description = "Get employee profile details")
    public Employee getEmployeeProfile(
            @ToolParam(description = "Employee ID") String employeeId) {

        return employeeService.getEmployeeByEmployeeId(employeeId);
    }

    @Tool(description = "Get reporting manager of an employee")
    public String getReportingManager(String employeeId) {
        return employeeService.getManagerName(employeeId);
    }

    @Tool(description = "Get employee manager name")
    public String getManager(String employeeId) {
        return employeeService.getManagerName(employeeId);
    }
}
