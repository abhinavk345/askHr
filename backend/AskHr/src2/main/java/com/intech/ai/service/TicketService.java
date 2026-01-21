package com.intech.ai.service;

import com.intech.ai.modal.TicketResponse;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TicketService {

    public TicketResponse createTicket(
            String employeeId,
            String category,
            String description) {

        return new TicketResponse(
                UUID.randomUUID().toString(),
                "OPEN",
                category
        );
    }
}
