package com.ticket.backend.controller;

import com.ticket.backend.dto.request.CardDeviceRequest;
import com.ticket.backend.dto.request.CreateTicketRequest;
import com.ticket.backend.dto.request.LoadMoneyRequest;
import com.ticket.backend.dto.response.TicketResponse;
import com.ticket.backend.model.Ticket;
import com.ticket.backend.model.ValidationMode;
import com.ticket.backend.service.TicketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    @PostMapping("/validate")
    public ResponseEntity<Map<String, String>> validate(@RequestBody CardDeviceRequest request) {
        ValidationMode mode = ticketService.validateTicket(
                request.getRfidCardId(),
                request.getDeviceId());
        return ResponseEntity.ok(Map.of("mode", mode.name()));
    }

    @PostMapping("/use")
    public ResponseEntity<Map<String, String>> use(@RequestBody CardDeviceRequest request) {
        String result = ticketService.useTicket(
                request.getRfidCardId(),
                request.getDeviceId());
        return ResponseEntity.ok(Map.of("result", result));
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

    @PostMapping("/{rfidCardId}/invalidate")
    public ResponseEntity<Void> invalidateTicket(@PathVariable String rfidCardId) {
        ticketService.invalidateTicket(rfidCardId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{rfidCardId}")
    public ResponseEntity<TicketResponse> getOneTicket(@PathVariable String rfidCardId) {
        return ResponseEntity.ok(TicketResponse.from(ticketService.getOneTicket(rfidCardId)));
    }
}
