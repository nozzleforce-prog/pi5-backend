package com.ticket.backend.service;

import com.ticket.backend.dto.request.CreateTicketRequest;
import com.ticket.backend.dto.response.TicketResponse;
import com.ticket.backend.model.Device;
import com.ticket.backend.model.Operation;
import com.ticket.backend.model.Ticket;
import com.ticket.backend.model.TicketStatus;
import com.ticket.backend.model.ValidationMode;
import com.ticket.backend.repository.DeviceRepository;
import com.ticket.backend.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceService deviceService;

    public TicketService(
            TicketRepository ticketRepository,
            DeviceRepository deviceRepository,
            DeviceService deviceService) {
        this.ticketRepository = ticketRepository;
        this.deviceRepository = deviceRepository;
        this.deviceService = deviceService;
    }

    public ValidationMode validateTicket(String rfidCardId, String deviceId) {
        if (isBlank(rfidCardId) || isBlank(deviceId)) {
            return ValidationMode.INVALID;
        }

        Optional<Ticket> ticketOpt = ticketRepository.findByBarcode(rfidCardId.trim());
        Optional<Device> deviceOpt = deviceRepository.findByDeviceId(deviceId.trim());

        if (ticketOpt.isEmpty() || deviceOpt.isEmpty()) {
            return ValidationMode.INVALID;
        }

        Ticket ticket = ticketOpt.get();
        Device device = deviceOpt.get();

        if (!device.isActive()) {
            return ValidationMode.INVALID;
        }

        if (!isTicketActive(ticket)) {
            return ValidationMode.INVALID;
        }

        Operation operation = deviceService.resolveOperation(device);
        if (ticket.getBalance() < operation.getOperationFee()) {
            return ValidationMode.INVALID;
        }

        return ticket.getMode();
    }

    public Ticket createTicket(CreateTicketRequest request) {
        if (request == null || isBlank(request.getRfidCardId())) {
            throw new IllegalArgumentException("rfidCardId is required");
        }
        if (request.getLoadAmount() < 0) {
            throw new IllegalArgumentException("loadAmount must be >= 0");
        }

        String cardId = request.getRfidCardId().trim();
        if (ticketRepository.findByBarcode(cardId).isPresent()) {
            throw new IllegalArgumentException("Ticket already exists for card: " + cardId);
        }

        Date expiration = Date.from(Instant.now().plus(30, ChronoUnit.DAYS));

        Ticket ticket = new Ticket();
        ticket.setBarcode(cardId);
        ticket.setName(request.getName());
        ticket.setNumber(request.getNumber());
        ticket.setBalance(request.getLoadAmount());
        ticket.setMode(request.getMode() != null ? request.getMode() : ValidationMode.BASIC);
        ticket.setCreatedAt(new Date());
        ticket.setExpiresAt(expiration);
        ticket.setStatus(TicketStatus.ACTIVE);

        return ticketRepository.save(ticket);
    }

    public Ticket loadMoney(String rfidCardId, int amount) {
        if (isBlank(rfidCardId)) {
            throw new IllegalArgumentException("rfidCardId is required");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        Ticket ticket = ticketRepository.findByBarcode(rfidCardId.trim())
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + rfidCardId));

        ticket.setBalance(ticket.getBalance() + amount);
        if (ticket.getStatus() == TicketStatus.EXPIRED && !isExpiredByDate(ticket)) {
            ticket.setStatus(TicketStatus.ACTIVE);
        }

        return ticketRepository.save(ticket);
    }

    public Ticket createMasterTicket(CreateTicketRequest request) {
        if (request == null || isBlank(request.getRfidCardId())) {
            throw new IllegalArgumentException("rfidCardId is required");
        }

        String cardId = request.getRfidCardId().trim();
        Date expiration = Date.from(Instant.now().plus(10, ChronoUnit.YEARS));

        Ticket ticket = ticketRepository.findByBarcode(cardId).orElse(new Ticket());
        ticket.setBarcode(cardId);
        ticket.setName(request.getName());
        ticket.setNumber(request.getNumber());
        ticket.setBalance(request.getLoadAmount() > 0 ? request.getLoadAmount() : ticket.getBalance());
        ticket.setMode(request.getMode() != null ? request.getMode() : ValidationMode.EXTRA);
        if (ticket.getCreatedAt() == null) {
            ticket.setCreatedAt(new Date());
        }
        ticket.setExpiresAt(expiration);
        ticket.setStatus(TicketStatus.ACTIVE);

        return ticketRepository.save(ticket);
    }

    public List<TicketResponse> getAllTickets() {
        invalidateExpiredTickets();
        return ticketRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(ticket -> ticket.getStatus() == TicketStatus.ACTIVE)
                .map(TicketResponse::from)
                .collect(Collectors.toList());
    }

    public void deleteTicket(String id) {
        ticketRepository.deleteById(id);
    }

    public void invalidateExpiredTickets() {
        Date now = new Date();
        for (Ticket ticket : ticketRepository.findAllByOrderByCreatedAtDesc()) {
            if (isExpiredByDate(ticket) && ticket.getStatus() == TicketStatus.ACTIVE) {
                ticket.setStatus(TicketStatus.EXPIRED);
                ticketRepository.save(ticket);
            }
        }
    }

    public String useTicket(String rfidCardId, String deviceId) {
        TicketUseResult result = useTicketWithResult(rfidCardId, deviceId);
        return result.message();
    }

    public TicketUseResult useTicketWithResult(String rfidCardId, String deviceId) {
        ValidationMode mode = validateTicket(rfidCardId, deviceId);
        if (mode == ValidationMode.INVALID) {
            return new TicketUseResult(false, 0, "Ticket invalid, not found, or insufficient balance");
        }

        Ticket ticket = ticketRepository.findByBarcode(rfidCardId.trim()).orElseThrow();
        Device device = deviceRepository.findByDeviceId(deviceId.trim()).orElseThrow();
        int fee = deviceService.operationFeeFor(device);

        int newBalance = ticket.getBalance() - fee;
        ticket.setBalance(newBalance);
        if (newBalance <= 0) {
            ticket.setStatus(TicketStatus.EXPIRED);
        }
        ticketRepository.save(ticket);

        return new TicketUseResult(true, newBalance, "OK: remaining balance=" + newBalance);
    }

    @Transactional
    public void invalidateTicket(String rfidCardId) {
        Ticket ticket = ticketRepository.findByBarcode(rfidCardId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + rfidCardId));

        if (ticket.getStatus() != TicketStatus.EXPIRED) {
            ticket.setStatus(TicketStatus.EXPIRED);
            ticketRepository.save(ticket);
        }
    }

    public Ticket getOneTicket(String rfidCardId) {
        return ticketRepository.findByBarcode(rfidCardId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + rfidCardId));
    }

    private boolean isTicketActive(Ticket ticket) {
        return ticket.getStatus() == TicketStatus.ACTIVE && !isExpiredByDate(ticket);
    }

    private boolean isExpiredByDate(Ticket ticket) {
        return ticket.getExpiresAt() != null && ticket.getExpiresAt().before(new Date());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
