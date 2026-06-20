package com.ticket.backend.service;

import com.ticket.backend.dto.response.CardOperationLogResponse;
import com.ticket.backend.model.CardOperationLog;
import com.ticket.backend.model.CardOperationType;
import com.ticket.backend.model.Ticket;
import com.ticket.backend.repository.CardOperationLogRepository;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CardOperationLogService {

    private final CardOperationLogRepository repository;
    private final CurrentUserService currentUserService;

    public CardOperationLogService(
            CardOperationLogRepository repository,
            CurrentUserService currentUserService) {
        this.repository = repository;
        this.currentUserService = currentUserService;
    }

    public List<CardOperationLogResponse> getAllLogs() {
        return getLogs(null, null);
    }

    public List<CardOperationLogResponse> getLogs(Date from, Date to) {
        List<CardOperationLog> logs;
        if (from != null && to != null) {
            logs = repository.findByPerformedAtBetweenOrderByPerformedAtDesc(from, to);
        } else if (from != null) {
            logs = repository.findByPerformedAtGreaterThanEqualOrderByPerformedAtDesc(from);
        } else if (to != null) {
            logs = repository.findByPerformedAtLessThanEqualOrderByPerformedAtDesc(to);
        } else {
            logs = repository.findAllByOrderByPerformedAtDesc();
        }
        return logs.stream()
                .map(CardOperationLogResponse::from)
                .collect(Collectors.toList());
    }

    public void recordCardCreated(Ticket ticket) {
        CardOperationLog log = baseLog(CardOperationType.CARD_CREATED, ticket);
        log.setAmount(ticket.getBalance());
        log.setBalanceBefore(0);
        log.setBalanceAfter(ticket.getBalance());
        log.setDetails("Card registered");
        repository.save(log);
    }

    public void recordBalanceLoaded(Ticket ticket, int amount, int balanceBefore) {
        CardOperationLog log = baseLog(CardOperationType.BALANCE_LOADED, ticket);
        log.setAmount(amount);
        log.setBalanceBefore(balanceBefore);
        log.setBalanceAfter(ticket.getBalance());
        log.setDetails("Balance loaded");
        repository.save(log);
    }

    public void recordCardUpdated(Ticket ticket, int balanceBefore, String details) {
        CardOperationLog log = baseLog(CardOperationType.CARD_UPDATED, ticket);
        log.setBalanceBefore(balanceBefore);
        log.setBalanceAfter(ticket.getBalance());
        log.setDetails(details);
        repository.save(log);
    }

    public void recordCardUsed(Ticket ticket, String deviceId, int fee, int balanceBefore) {
        CardOperationLog log = baseLog(CardOperationType.CARD_USED, ticket, "SYSTEM");
        log.setDeviceId(deviceId);
        log.setAmount(fee);
        log.setBalanceBefore(balanceBefore);
        log.setBalanceAfter(ticket.getBalance());
        log.setDetails("Card used at device " + deviceId);
        repository.save(log);
    }

    private CardOperationLog baseLog(CardOperationType type, Ticket ticket) {
        return baseLog(type, ticket, currentUserService.getCurrentUsername().orElse(null));
    }

    private CardOperationLog baseLog(CardOperationType type, Ticket ticket, String performedBy) {
        CardOperationLog log = new CardOperationLog();
        log.setType(type);
        log.setRfidCardId(ticket.getBarcode());
        log.setName(ticket.getName());
        log.setNumber(ticket.getNumber());
        log.setStatus(ticket.getStatus());
        log.setPerformedAt(new Date());
        log.setPerformedBy(performedBy);
        return log;
    }
}
