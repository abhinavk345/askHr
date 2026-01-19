package com.intech.ai.tools;

import com.intech.ai.service.PolicyService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PolicyTools {

    private final PolicyService policyService;

    public PolicyTools(PolicyService policyService) {
        this.policyService = policyService;
    }

    @Tool(description = "List all HR policies")
    public List<String> listPolicies() {
        return policyService.getPolicies();
    }

    @Tool(description = "Get HR policy details by name")
    public String getPolicy(String policyName) {
        return policyService.getPolicy(policyName);
    }
}
