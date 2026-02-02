package com.intech.ai.tools;

import com.intech.ai.modal.TicketResponse;
import com.intech.ai.service.TicketService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class TicketTools {

    private final TicketService ticketService;

    public TicketTools(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Tool(description = "Raise HR or IT support ticket")
    public TicketResponse raiseTicket(
            String employeeId,
            String category,
            String description) {

        return ticketService.createTicket(employeeId, category, description);
    }
}
