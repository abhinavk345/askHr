package com.intech.ai.service;import com.intech.ai.modal.Ticket;

import com.intech.ai.modal.TicketResponse;
import com.intech.ai.repository.TicketRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;

    public TicketService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    public TicketResponse createTicket(
            String employeeId,
            String category,
            String description) {

        Ticket ticket = new Ticket();
        ticket.setEmployeeId(employeeId);
        ticket.setCategory(category);
        ticket.setDescription(description);
        ticket.setStatus("OPEN");
        ticket.setCreatedAt(LocalDateTime.now());

        Ticket saved = ticketRepository.save(ticket);

        return new TicketResponse(
                saved.getId().toString(),
                saved.getStatus(),
                saved.getCategory()
        );
    }
}
