package com.intech.ai.config;

import com.intech.ai.service.LeaveTools;
import com.intech.ai.tools.EmployeeTools;
import com.intech.ai.tools.PolicyTools;
import com.intech.ai.tools.TicketTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ChatMemoryConfig {

    @Autowired
    private EmployeeTools employeeTools;
    @Autowired
    private LeaveTools leaveTools;
    @Autowired
    private PolicyTools policyTools;
    @Autowired
    private TicketTools ticketTools;

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {

        SimpleLoggerAdvisor loggerAdvisor = new SimpleLoggerAdvisor();


        MessageChatMemoryAdvisor memoryAdvisor =
                MessageChatMemoryAdvisor.builder(chatMemory).build();

        return chatClientBuilder
                .defaultSystem("""
                    You are an HR assistant for INTECH INDIA.
                    Your responsibility is to answer employee questions strictly based on the HR Leave Policy provided.
                    If a question is outside this policy, politely respond that the information is not available.

                    IMPORTANT RULE:
                    If the question is NOT related to HR leave policies, you MUST respond ONLY with:
                    "I’m sorry, this information is not available as per the HR Leave Policy."
                    Do not explain.
                    Do not add examples.
                    Do not provide general knowledge.
                """)
                .defaultAdvisors(List.of(
                        loggerAdvisor,
                        memoryAdvisor
                ))
                .defaultTools(employeeTools,
                        leaveTools,
                        policyTools,
                        ticketTools)
                .build();
    }

}
