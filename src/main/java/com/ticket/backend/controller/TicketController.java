package com.ticket.backend.controller;

import com.ticket.backend.dto.request.CreateTicketRequest;
import com.ticket.backend.dto.request.LoadMoneyRequest;
import com.ticket.backend.dto.request.UpdateTicketRequest;
import com.ticket.backend.dto.response.TicketResponse;
import com.ticket.backend.model.Ticket;
import com.ticket.backend.model.TicketStatus;
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
        return ResponseEntity.ok(TicketResponse.from(ticket));
    }

    @PostMapping("/load-money")
    public ResponseEntity<TicketResponse> loadMoney(@RequestBody LoadMoneyRequest request) {
        Ticket ticket = ticketService.loadMoney(request.getRfidCardId(), request.getAmount());
        return ResponseEntity.ok(TicketResponse.from(ticket));
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

    @PostMapping("/{rfidCardId}/deactivate")
    public ResponseEntity<TicketResponse> deactivateTicket(@PathVariable String rfidCardId) {
        ticketService.setTicketStatus(rfidCardId, TicketStatus.INACTIVE);
        return ResponseEntity.ok(TicketResponse.from(ticketService.getOneTicket(rfidCardId)));
    }

    @PostMapping("/{rfidCardId}/activate")
    public ResponseEntity<TicketResponse> activateTicket(@PathVariable String rfidCardId) {
        ticketService.setTicketStatus(rfidCardId, TicketStatus.ACTIVE);
        return ResponseEntity.ok(TicketResponse.from(ticketService.getOneTicket(rfidCardId)));
    }

    @GetMapping("/{rfidCardId}")
    public ResponseEntity<TicketResponse> getOneTicket(@PathVariable String rfidCardId) {
        return ResponseEntity.ok(TicketResponse.from(ticketService.getOneTicket(rfidCardId)));
    }

    @PutMapping("/{rfidCardId}")
    public ResponseEntity<TicketResponse> updateTicket(
            @PathVariable String rfidCardId,
            @RequestBody UpdateTicketRequest request) {
        Ticket ticket = ticketService.updateTicket(rfidCardId, request);
        return ResponseEntity.ok(TicketResponse.from(ticket));
    }
}
