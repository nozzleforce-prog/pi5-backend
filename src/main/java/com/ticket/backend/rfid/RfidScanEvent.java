package com.ticket.backend.rfid;

import java.time.Instant;

public record RfidScanEvent(
        String deviceId,
        String cardId,
        String remoteAddress,
        Instant receivedAt,
        RfidReaderType readerType
) {
    public RfidScanEvent(String deviceId, String cardId, String remoteAddress, Instant receivedAt) {
        this(deviceId, cardId, remoteAddress, receivedAt, RfidReaderType.RP2350);
    }
}
