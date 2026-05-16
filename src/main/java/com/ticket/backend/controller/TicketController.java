package com.ticket.backend.controller;

import com.ticket.backend.dto.request.CreateTicketRequest;
import com.ticket.backend.dto.response.TicketResponse;
import com.ticket.backend.model.Ticket;
import com.ticket.backend.service.TicketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {
    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping(produces = "application/json")
    public ResponseEntity<TicketResponse> createTicket(@RequestBody CreateTicketRequest request) {
        Ticket ticket = ticketService.createTicket(request);

        TicketResponse response = new TicketResponse(
                ticket.getBarcode(),
                ticket.getMode(),
                ticket.getPrice(),
                ticket.getExpiresAt(),
                ticket.getCreatedAt()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<TicketResponse>> getAllTickets() {
        return ResponseEntity.ok(ticketService.getAllTickets());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTicket(@PathVariable String id) {
        ticketService.deleteTicket(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{ticketCode}/invalidate")
    public ResponseEntity<Void> invalidateTicket(@PathVariable String ticketCode) {
        ticketService.invalidateTicket(ticketCode);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{ticketCode}/getOne")
    public ResponseEntity<TicketResponse> getOneTicket(@PathVariable String ticketCode) {
        Ticket ticket = ticketService.getOneTicket(ticketCode);

        TicketResponse response = new TicketResponse(
                ticket.getBarcode(),
                ticket.getMode(),
                ticket.getPrice(),
                ticket.getExpiresAt(),
                ticket.getCreatedAt()
        );

        return ResponseEntity.ok(response);
    }
}

