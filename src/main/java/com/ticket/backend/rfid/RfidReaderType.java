package com.ticket.backend.rfid;

public enum RfidReaderType {
    RP2350,
    P4_NANO;

    public static RfidReaderType fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return P4_NANO;
        }
        return switch (value.trim().toLowerCase().replace('_', '-')) {
            case "rp2350", "rp2040", "ch9120" -> RP2350;
            case "p4-nano", "p4nano", "esp32-p4", "nano-p4" -> P4_NANO;
            default -> P4_NANO;
        };
    }
}
