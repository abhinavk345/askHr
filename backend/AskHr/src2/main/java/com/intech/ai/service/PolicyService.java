package com.intech.ai.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PolicyService {

    private static final Map<String, String> POLICIES = Map.of(
            "Leave Policy", "Employees are entitled to 24 leaves per year",
            "WFH Policy", "WFH allowed up to 2 days per week"
    );

    public List<String> getPolicies() {
        return new ArrayList<>(POLICIES.keySet());
    }

    public String getPolicy(String name) {
        return POLICIES.getOrDefault(name, "Policy not found");
    }
}
