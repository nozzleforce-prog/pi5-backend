package com.ticket.backend.rfid;

/**
 * RP2350 / RFID uc birim TCP satir protokolu.
 * <ul>
 *   <li>{@code START,...} — PLC state onaylandi, makine basladi</li>
 *   <li>{@code STOP,...} — makine durdu</li>
 *   <li>{@code FAIL,...} — hata</li>
 *   <li>{@code BUSY,...} — (eski uyumluluk)</li>
 *   <li>{@code OK,...} — (eski uyumluluk)</li>
 * </ul>
 */
public final class RfidDeviceProtocol {

    private RfidDeviceProtocol() {
    }

    public static String start(
            String deviceId,
            String cardId,
            int durationSeconds,
            int balance,
            String operationName) {
        return "START,deviceId=" + deviceId
                + ",cardId=" + cardId
                + ",durationSec=" + durationSeconds
                + ",balance=" + balance
                + ",operation=" + sanitize(operationName);
    }

    public static String stop(String deviceId, String cardId, int balance) {
        return "STOP,deviceId=" + deviceId
                + ",cardId=" + cardId
                + ",balance=" + balance;
    }

    public static String fail(String deviceId, String reason) {
        return "FAIL,deviceId=" + deviceId + ",reason=" + sanitize(reason);
    }

    public static String ok(String deviceId, String cardId, int balance) {
        return "OK,deviceId=" + deviceId + ",cardId=" + cardId + ",balance=" + balance;
    }

    public static String busy(String deviceId) {
        return "BUSY,deviceId=" + deviceId + ",reason=DEVICE_RUNNING";
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace(',', '_').trim();
    }
}
