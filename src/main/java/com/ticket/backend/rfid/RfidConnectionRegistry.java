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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Canli TCP baglantilari (ESP32-P4-NANO / RP2350). {@link #maxConnections} kadar eszamanli oturum desteklenir.
 */
@Component
public class RfidConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(RfidConnectionRegistry.class);

    private final Map<String, RfidUnitConnection> byDeviceId = new ConcurrentHashMap<>();
    private final Map<String, String> deviceIdByHost = new ConcurrentHashMap<>();
    private final Map<String, String> hostByDeviceId = new ConcurrentHashMap<>();
    private final Map<String, RfidReaderType> readerTypeByDeviceId = new ConcurrentHashMap<>();

    private volatile int maxConnections = 16;
    private final ScheduledExecutorService scanResumeScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "rfid-scan-resume");
                t.setDaemon(true);
                return t;
            });

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
            String normalizedHost = host.trim();
            String normalizedDeviceId = deviceId.trim();
            deviceIdByHost.put(normalizedHost, normalizedDeviceId);
            hostByDeviceId.put(normalizedDeviceId, normalizedHost);
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
            if (previous.socket() == socket) {
                return;
            }
            log.info("RFID baglanti yenilendi: deviceId={} host={}", deviceId, remoteHost);
            previous.closeQuietly();
        } else {
            log.info("RFID baglanti kayit: deviceId={} host={} (toplam {})", deviceId, remoteHost, byDeviceId.size());
        }
        if (remoteHost != null) {
            deviceIdByHost.put(remoteHost, deviceId);
            hostByDeviceId.put(deviceId, remoteHost);
        }
    }

    public void unregister(String deviceId) {
        RfidUnitConnection removed = byDeviceId.remove(deviceId);
        if (removed != null) {
            deviceIdByHost.entrySet().removeIf(e -> deviceId.equals(e.getValue()));
            hostByDeviceId.remove(deviceId);
            removed.closeQuietly();
            log.info("RFID baglanti kaldirildi: deviceId={} (kalan {})", deviceId, byDeviceId.size());
        }
    }

    public void unregisterSocket(Socket socket) {
        if (socket == null) {
            return;
        }
        byDeviceId.entrySet().removeIf(entry -> {
            boolean sameSocket = entry.getValue().socket() == socket;
            if (sameSocket) {
                String deviceId = entry.getKey();
                deviceIdByHost.entrySet().removeIf(e -> deviceId.equals(e.getValue()));
                hostByDeviceId.remove(deviceId);
                log.info("RFID baglanti kaldirildi: deviceId={} (kalan {})", deviceId, byDeviceId.size());
            }
            return sameSocket;
        });
        closeQuietly(socket);
    }

    public boolean isAcceptingScans(String deviceId) {
        RfidUnitConnection c = byDeviceId.get(deviceId);
        if (c == null) {
            return true;
        }
        return c.isRfidEnabled();
    }

    public boolean tryBeginSession(String deviceId) {
        RfidUnitConnection c = byDeviceId.get(deviceId);
        if (c == null) {
            /* TCP baglantisi yok — PLC / bilet akisi yine de calisabilir */
            return true;
        }
        if (!c.tryBeginSession()) {
            return false;
        }
        c.setRfidEnabled(false);
        return true;
    }

    public boolean hasLiveConnection(String deviceId) {
        return byDeviceId.containsKey(deviceId);
    }

    public void endSession(String deviceId) {
        RfidUnitConnection c = byDeviceId.get(deviceId);
        if (c != null) {
            c.endSession();
        }
    }

    /** Oturumu kapat; taramayi gecikme sonrasi tekrar ac (PLC timeout vb.). */
    public void endSessionWithScanDelay(String deviceId, long delayMs) {
        RfidUnitConnection c = byDeviceId.get(deviceId);
        if (c == null) {
            return;
        }
        c.endSessionKeepingScanDisabled();
        long delay = Math.max(0, delayMs);
        scanResumeScheduler.schedule(() -> {
            RfidUnitConnection live = byDeviceId.get(deviceId);
            if (live != null) {
                live.setRfidEnabled(true);
                log.info("RFID tarama tekrar acildi: deviceId={}", deviceId);
            }
        }, delay, TimeUnit.MILLISECONDS);
        log.info("RFID tarama {} ms kapali: deviceId={}", delay, deviceId);
    }

    public void setReaderType(String deviceId, RfidReaderType readerType) {
        if (deviceId != null && !deviceId.isBlank() && readerType != null) {
            readerTypeByDeviceId.put(deviceId.trim(), readerType);
        }
    }

    public RfidReaderType resolveReaderType(String deviceId, RfidReaderType eventType) {
        if (deviceId != null && readerTypeByDeviceId.containsKey(deviceId)) {
            return readerTypeByDeviceId.get(deviceId);
        }
        return eventType != null ? eventType : RfidReaderType.P4_NANO;
    }

    public boolean sendToDevice(String deviceId, String line) {
        RfidUnitConnection c = byDeviceId.get(deviceId);
        if (c == null) {
            String host = hostByDeviceId.get(deviceId);
            if (host != null) {
                c = byDeviceId.values().stream()
                        .filter(conn -> host.equals(conn.remoteHost()))
                        .findFirst()
                        .orElse(null);
            }
        }
        if (c == null) {
            log.warn("RFID yanit gonderilemedi — baglanti yok: deviceId={}", deviceId);
            return false;
        }
        return c.sendLine(line);
    }

    public void sendLinesToDevice(String deviceId, String... lines) {
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                sendToDevice(deviceId, line);
            }
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (Exception ignored) {
        }
    }
}
