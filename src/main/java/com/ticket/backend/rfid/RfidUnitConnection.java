package com.ticket.backend.rfid;

import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/** Tek RP2350 / RFID uc birim TCP oturumu. */
public final class RfidUnitConnection {

    private final String deviceId;
    private final String remoteHost;
    private final Socket socket;
    private final PrintWriter writer;
    private final AtomicBoolean rfidEnabled = new AtomicBoolean(true);
    private final AtomicBoolean sessionBusy = new AtomicBoolean(false);

    public RfidUnitConnection(String deviceId, String remoteHost, Socket socket, PrintWriter writer) {
        this.deviceId = deviceId;
        this.remoteHost = remoteHost;
        this.socket = socket;
        this.writer = writer;
    }

    public String deviceId() {
        return deviceId;
    }

    public String remoteHost() {
        return remoteHost;
    }

    public boolean isRfidEnabled() {
        return rfidEnabled.get() && !sessionBusy.get();
    }

    public boolean tryBeginSession() {
        if (!rfidEnabled.get()) {
            return false;
        }
        return sessionBusy.compareAndSet(false, true);
    }

    public void endSession() {
        sessionBusy.set(false);
        rfidEnabled.set(true);
    }

    public void setRfidEnabled(boolean enabled) {
        rfidEnabled.set(enabled);
    }

    public synchronized boolean sendLine(String line) {
        if (writer == null || socket.isClosed()) {
            return false;
        }
        writer.print(line);
        if (!line.endsWith("\n")) {
            writer.print("\r\n");
        }
        writer.flush();
        return !writer.checkError();
    }

    public void closeQuietly() {
        try {
            socket.close();
        } catch (Exception ignored) {
        }
    }
}
