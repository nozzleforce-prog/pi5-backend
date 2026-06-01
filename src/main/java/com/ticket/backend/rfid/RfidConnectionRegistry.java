package com.ticket.backend.rfid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Canli TCP baglantilari (RP2350). {@link #maxConnections} kadar eszamanli oturum desteklenir.
 */
@Component
public class RfidConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(RfidConnectionRegistry.class);

    private final Map<String, RfidUnitConnection> byDeviceId = new ConcurrentHashMap<>();
    private final Map<String, String> deviceIdByHost = new ConcurrentHashMap<>();

    private volatile int maxConnections = 16;

    public void setMaxConnections(int max) {
        this.maxConnections = Math.max(1, max);
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public int activeConnectionCount() {
        return byDeviceId.size();
    }

    public Set<String> connectedDeviceIds() {
        return Set.copyOf(byDeviceId.keySet());
    }

    public void bindHostToDeviceId(String host, String deviceId) {
        if (host != null && deviceId != null && !host.isBlank() && !deviceId.isBlank()) {
            deviceIdByHost.put(host.trim(), deviceId.trim());
        }
    }

    public Optional<String> resolveDeviceId(String host, String fromMessageDeviceId) {
        if (fromMessageDeviceId != null && !fromMessageDeviceId.isBlank() && !"unknown".equals(fromMessageDeviceId)) {
            return Optional.of(fromMessageDeviceId.trim());
        }
        if (host != null && !host.isBlank()) {
            return Optional.ofNullable(deviceIdByHost.get(host.trim()));
        }
        return Optional.empty();
    }

    public void register(String deviceId, String remoteHost, Socket socket, PrintWriter writer) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }
        if (byDeviceId.size() >= maxConnections && !byDeviceId.containsKey(deviceId)) {
            log.warn("RFID max baglanti ({}) dolu — {} reddedildi", maxConnections, deviceId);
            closeQuietly(socket);
            return;
        }
        RfidUnitConnection conn = new RfidUnitConnection(deviceId, remoteHost, socket, writer);
        RfidUnitConnection previous = byDeviceId.put(deviceId, conn);
        if (previous != null) {
            log.info("RFID baglanti yenilendi: deviceId={} host={}", deviceId, remoteHost);
            previous.closeQuietly();
        } else {
            log.info("RFID baglanti kayit: deviceId={} host={} (toplam {})", deviceId, remoteHost, byDeviceId.size());
        }
        if (remoteHost != null) {
            deviceIdByHost.put(remoteHost, deviceId);
        }
    }

    public void unregister(String deviceId) {
        RfidUnitConnection removed = byDeviceId.remove(deviceId);
        if (removed != null) {
            deviceIdByHost.entrySet().removeIf(e -> deviceId.equals(e.getValue()));
            removed.closeQuietly();
            log.info("RFID baglanti kaldirildi: deviceId={} (kalan {})", deviceId, byDeviceId.size());
        }
    }

    public boolean isAcceptingScans(String deviceId) {
        RfidUnitConnection c = byDeviceId.get(deviceId);
        return c != null && c.isRfidEnabled();
    }

    public boolean tryBeginSession(String deviceId) {
        RfidUnitConnection c = byDeviceId.get(deviceId);
        if (c == null) {
            return false;
        }
        c.setRfidEnabled(false);
        return c.tryBeginSession();
    }

    public void endSession(String deviceId) {
        RfidUnitConnection c = byDeviceId.get(deviceId);
        if (c != null) {
            c.endSession();
        }
    }

    public boolean sendToDevice(String deviceId, String line) {
        RfidUnitConnection c = byDeviceId.get(deviceId);
        if (c == null) {
            log.warn("RFID yanit gonderilemedi — baglanti yok: deviceId={}", deviceId);
            return false;
        }
        return c.sendLine(line);
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (Exception ignored) {
        }
    }
}
