package com.ticket.backend.service;

import com.ticket.backend.constants.TicketConstants;
import com.ticket.backend.dto.request.CreateTicketRequest;
import com.ticket.backend.dto.response.TicketResponse;
import com.ticket.backend.model.Ticket;
import com.ticket.backend.model.TicketStatus;
import com.ticket.backend.model.ValidationMode;
import org.springframework.stereotype.Service;
import com.ticket.backend.repository.TicketRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TicketService {
    private final TicketRepository ticketRepository;

    public TicketService(TicketRepository repository) {
        this.ticketRepository = repository;
    }

    public ValidationMode validateTicket(String barcode) {
            Optional<Ticket> ticketOpt = ticketRepository.findByBarcode(barcode);

        if (ticketOpt.isEmpty()) {
            return ValidationMode.INVALID;
        }

        Ticket ticket = ticketOpt.get();
        Date now = new Date();

        if (ticket.getExpiresAt().before(now) || ticket.getStatus() != TicketStatus.ACTIVE) {
            ticket.setStatus(TicketStatus.EXPIRED);
            ticketRepository.save(ticket);
            return ValidationMode.INVALID;
        }

        return ticket.getMode();
    }

    public Ticket createTicket(CreateTicketRequest request) {
        String code = UUID.randomUUID()
                .toString()
                .replaceAll("[^A-Za-z0-9]", ""); // keep only letters and digits

        Date expiration = Date.from(
                Instant.now().plus(30, ChronoUnit.DAYS)
        );

        Ticket ticket = new Ticket();
        ticket.setBarcode(code);
        ticket.setMode(request.getMode());
        ticket.setPrice(TicketConstants.modeToPrice.get(request.getMode()));
        ticket.setCreatedAt(new Date());
        ticket.setExpiresAt(expiration);
        ticket.setStatus(TicketStatus.ACTIVE);

        return ticketRepository.save(ticket);
    }

    public Ticket createMasterTicket(CreateTicketRequest request) {
        String code = UUID.randomUUID()
                .toString()
                .replaceAll("[^A-Za-z0-9]", "");

        // 10 years expiration
        Date expiration = Date.from(Instant.now().plus(10, ChronoUnit.YEARS));

        Ticket ticket = new Ticket();
        ticket.setBarcode(code);
        ticket.setMode(request.getMode());
        ticket.setPrice(TicketConstants.modeToPrice.get(request.getMode()));
        ticket.setCreatedAt(new Date());
        ticket.setExpiresAt(expiration);
        ticket.setStatus(TicketStatus.ACTIVE);

        return ticketRepository.save(ticket);
    }

    public List<TicketResponse> getAllTickets() {
        this.invalidateExpiredTickets();
        return ticketRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(ticket -> ticket.getStatus() == TicketStatus.ACTIVE)
                .map(ticket ->  new TicketResponse(
                        ticket.getBarcode(),
                        ticket.getMode(),
                        ticket.getPrice(),
                        ticket.getExpiresAt(),
                        ticket.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    public void deleteTicket(String id) {
        ticketRepository.deleteById(id);
    }

    private String generateBarcode() {
        return "TICKET-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    public void invalidateExpiredTickets() {
        Date now = new Date();
        List<Ticket> tickets = ticketRepository.findAllByOrderByCreatedAtDesc();

        for (Ticket ticket : tickets) {
            if (ticket.getExpiresAt().before(now) && ticket.getStatus() == TicketStatus.ACTIVE) {
                ticket.setStatus(TicketStatus.EXPIRED);
                ticketRepository.save(ticket);
            }
        }
    }

    public String useTicket(String barcode) {
        Optional<Ticket> ticketOpt = ticketRepository.findByBarcode(barcode);

        if (ticketOpt.isEmpty()) {
            return "Ticket not found";
        }

        Ticket ticket = ticketOpt.get();
        Date now = new Date();

        if (ticket.getExpiresAt().before(now) || ticket.getStatus() != TicketStatus.ACTIVE) {
            return "Ticket is expired or inactive";
        }

        ticket.setStatus(TicketStatus.EXPIRED);
        ticketRepository.save(ticket);
        return "Ticket used successfully";
    }

    @Transactional
    public void invalidateTicket(String ticketCode) {
        Ticket ticket = ticketRepository.findByBarcode(ticketCode)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketCode));

        if (ticket.getStatus() != TicketStatus.EXPIRED) {
            ticket.setStatus(TicketStatus.EXPIRED);
            ticketRepository.save(ticket);
        }
    }

    public Ticket getOneTicket(String ticketCode) {
        return ticketRepository.findByBarcode(ticketCode)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketCode));
    }
}
