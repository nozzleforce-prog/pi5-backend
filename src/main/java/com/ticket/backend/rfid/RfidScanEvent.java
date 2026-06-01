package com.ticket.backend.rfid;

import java.time.Instant;

public record RfidScanEvent(
        String deviceId,
        String cardId,
        String remoteAddress,
        Instant receivedAt
) {}
