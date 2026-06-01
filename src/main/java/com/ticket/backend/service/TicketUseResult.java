package com.ticket.backend.service;

public record TicketUseResult(boolean success, int remainingBalance, String message) {}
