package com.ticket.backend.rfid;

/**
 * ESP32-P4-NANO uc birim TCP protokolu (scan_session.cpp ile uyumlu).
 */
public final class NanoP4DeviceProtocol {

    public static final int OPERATION_MESSAGE_ID = 3;

    private NanoP4DeviceProtocol() {
    }

    public static String operationLine(int operationId, int fee) {
        return "OPERASYON:" + Math.max(0, operationId) + " UCRET:" + Math.max(0, fee);
    }

    public static String balanceLine(int balance) {
        return String.format("BAKIYE:%.2f", (double) balance);
    }

    public static String machineStartedLine(boolean started) {
        return "MAKINE_BASLADI:" + (started ? 1 : 0);
    }

    public static String durationLine(int durationSeconds) {
        return "SURE:" + Math.max(0, durationSeconds);
    }

    public static String machineDoneLine() {
        return "MAKINE_BITTI";
    }

    public static String okLine() {
        return "OK";
    }
}
