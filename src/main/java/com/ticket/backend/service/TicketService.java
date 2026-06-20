package com.ticket.backend.service;

import com.ticket.backend.dto.request.CreateTicketRequest;
import com.ticket.backend.dto.request.UpdateTicketRequest;
import com.ticket.backend.dto.response.TicketResponse;
import com.ticket.backend.model.Device;
import com.ticket.backend.model.Operation;
import com.ticket.backend.model.Ticket;
import com.ticket.backend.model.TicketStatus;
import com.ticket.backend.repository.DeviceRepository;
import com.ticket.backend.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceService deviceService;
    private final CardOperationLogService cardOperationLogService;

    public TicketService(
            TicketRepository ticketRepository,
            DeviceRepository deviceRepository,
            DeviceService deviceService,
            CardOperationLogService cardOperationLogService) {
        this.ticketRepository = ticketRepository;
        this.deviceRepository = deviceRepository;
        this.deviceService = deviceService;
        this.cardOperationLogService = cardOperationLogService;
    }

    public TicketStatus validateTicket(String rfidCardId, String deviceId) {
        if (isBlank(rfidCardId) || isBlank(deviceId)) {
            return TicketStatus.INACTIVE;
        }

        Optional<Ticket> ticketOpt = ticketRepository.findByBarcode(rfidCardId.trim());
        Optional<Device> deviceOpt = deviceRepository.findByDeviceId(deviceId.trim());

        if (ticketOpt.isEmpty() || deviceOpt.isEmpty()) {
            return TicketStatus.INACTIVE;
        }

        Ticket ticket = ticketOpt.get();
        Device device = deviceOpt.get();

        if (!device.isActive()) {
            return TicketStatus.INACTIVE;
        }

        if (!isTicketUsable(ticket)) {
            return TicketStatus.INACTIVE;
        }

        Operation operation = deviceService.resolveOperation(device);
        if (ticket.getBalance() < operation.getOperationFee()) {
            return TicketStatus.INACTIVE;
        }

        return TicketStatus.ACTIVE;
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

        Ticket ticket = new Ticket();
        ticket.setBarcode(cardId);
        ticket.setName(request.getName());
        ticket.setNumber(request.getNumber());
        ticket.setBalance(request.getLoadAmount());
        ticket.setCreatedAt(new Date());
        ticket.setStatus(TicketStatus.ACTIVE);

        Ticket saved = ticketRepository.save(ticket);
        cardOperationLogService.recordCardCreated(saved);
        return saved;
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

        int balanceBefore = ticket.getBalance();
        ticket.setBalance(ticket.getBalance() + amount);
        if (ticket.getStatus() == TicketStatus.INACTIVE && ticket.getBalance() > 0) {
            ticket.setStatus(TicketStatus.ACTIVE);
        }

        Ticket saved = ticketRepository.save(ticket);
        cardOperationLogService.recordBalanceLoaded(saved, amount, balanceBefore);
        return saved;
    }

    public Ticket createMasterTicket(CreateTicketRequest request) {
        if (request == null || isBlank(request.getRfidCardId())) {
            throw new IllegalArgumentException("rfidCardId is required");
        }

        String cardId = request.getRfidCardId().trim();

        Ticket ticket = ticketRepository.findByBarcode(cardId).orElse(new Ticket());
        ticket.setBarcode(cardId);
        ticket.setName(request.getName());
        ticket.setNumber(request.getNumber());
        ticket.setBalance(request.getLoadAmount() > 0 ? request.getLoadAmount() : ticket.getBalance());
        if (ticket.getCreatedAt() == null) {
            ticket.setCreatedAt(new Date());
        }
        ticket.setStatus(TicketStatus.ACTIVE);

        return ticketRepository.save(ticket);
    }

    public List<TicketResponse> getAllTickets() {
        return ticketRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(TicketResponse::from)
                .collect(Collectors.toList());
    }

    public void deleteTicket(String id) {
        ticketRepository.deleteById(id);
    }

    public String useTicket(String rfidCardId, String deviceId) {
        TicketUseResult result = useTicketWithResult(rfidCardId, deviceId);
        return result.message();
    }

    public TicketUseResult useTicketWithResult(String rfidCardId, String deviceId) {
        if (validateTicket(rfidCardId, deviceId) != TicketStatus.ACTIVE) {
            return new TicketUseResult(false, 0, "Ticket invalid, not found, inactive, or insufficient balance");
        }

        Ticket ticket = ticketRepository.findByBarcode(rfidCardId.trim()).orElseThrow();
        Device device = deviceRepository.findByDeviceId(deviceId.trim()).orElseThrow();
        int fee = deviceService.operationFeeFor(device);
        int balanceBefore = ticket.getBalance();

        int newBalance = ticket.getBalance() - fee;
        ticket.setBalance(newBalance);
        if (newBalance <= 0) {
            ticket.setStatus(TicketStatus.INACTIVE);
        }
        ticketRepository.save(ticket);
        cardOperationLogService.recordCardUsed(ticket, deviceId.trim(), fee, balanceBefore);

        return new TicketUseResult(true, newBalance, "OK: remaining balance=" + newBalance);
    }

    @Transactional
    public void setTicketStatus(String rfidCardId, TicketStatus status) {
        Ticket ticket = ticketRepository.findByBarcode(rfidCardId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + rfidCardId));
        ticket.setStatus(status);
        ticketRepository.save(ticket);
    }

    public Ticket getOneTicket(String rfidCardId) {
        return ticketRepository.findByBarcode(rfidCardId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + rfidCardId));
    }

    public Ticket updateTicket(String rfidCardId, UpdateTicketRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Update body is required");
        }
        Ticket ticket = getOneTicket(rfidCardId);
        int balanceBefore = ticket.getBalance();
        List<String> changes = new ArrayList<>();

        if (request.getName() != null) {
            String name = request.getName().trim();
            String newName = name.isEmpty() ? null : name;
            if (!Objects.equals(ticket.getName(), newName)) {
                changes.add("name: " + displayValue(ticket.getName()) + " -> " + displayValue(newName));
            }
            ticket.setName(newName);
        }
        if (request.getNumber() != null) {
            String number = request.getNumber().trim();
            String newNumber = number.isEmpty() ? null : number;
            if (!Objects.equals(ticket.getNumber(), newNumber)) {
                changes.add("number: " + displayValue(ticket.getNumber()) + " -> " + displayValue(newNumber));
            }
            ticket.setNumber(newNumber);
        }
        if (request.getBalance() != null) {
            if (request.getBalance() < 0) {
                throw new IllegalArgumentException("balance must be >= 0");
            }
            if (ticket.getBalance() != request.getBalance()) {
                changes.add("balance: " + ticket.getBalance() + " -> " + request.getBalance());
            }
            ticket.setBalance(request.getBalance());
        }
        if (request.getStatus() != null) {
            if (ticket.getStatus() != request.getStatus()) {
                changes.add("status: " + ticket.getStatus() + " -> " + request.getStatus());
            }
            ticket.setStatus(request.getStatus());
        }

        Ticket saved = ticketRepository.save(ticket);
        if (!changes.isEmpty()) {
            cardOperationLogService.recordCardUpdated(saved, balanceBefore, String.join("; ", changes));
        }
        return saved;
    }

    private static String displayValue(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private boolean isTicketUsable(Ticket ticket) {
        return ticket.getStatus() == TicketStatus.ACTIVE;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
