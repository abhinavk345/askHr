package com.intech.ai.tools;

import com.intech.ai.service.PolicyService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class PolicyTools {

    private final PolicyService policyService;

    public PolicyTools(PolicyService policyService) {
        this.policyService = policyService;
    }

    @Tool(description = "Answer HR policy related questions using official HR documents")
    public String getPolicy(String question) throws Exception {
        return policyService.answerPolicy(question);
    }
}
