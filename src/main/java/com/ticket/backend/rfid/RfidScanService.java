package com.ticket.backend.rfid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class RfidScanService {

    private static final Logger log = LoggerFactory.getLogger(RfidScanService.class);

    private final BlockingQueue<RfidScanEvent> scanQueue = new LinkedBlockingQueue<>();
    private final Map<String, String> deviceIdByHost = new ConcurrentHashMap<>();
    private final RfidConnectionRegistry connectionRegistry;

    @Value("${rfid.console.print:true}")
    private boolean consolePrint;

    public RfidScanService(RfidConnectionRegistry connectionRegistry) {
        this.connectionRegistry = connectionRegistry;
    }

    public void setDeviceIdForHost(String host, String deviceId) {
        String key = normalizeHost(host);
        if (!key.isEmpty() && deviceId != null && !deviceId.isBlank()) {
            deviceIdByHost.put(key, deviceId.trim());
        }
    }

    public void handleLine(String line, String remoteAddress) {
        parseLine(line, remoteAddress).ifPresent(this::enqueueScan);
    }

    public void enqueueScan(RfidScanEvent event) {
        publishScan(event);
    }

    private void publishScan(RfidScanEvent event) {
        scanQueue.offer(event);
        log.info("[RFID] deviceId={} cardId={} from {}", event.deviceId(), event.cardId(), event.remoteAddress());
        if (consolePrint) {
            System.out.printf("%n>>> KART ID: %s  (cihaz: %s)  [%s]%n%n",
                    event.cardId(), event.deviceId(), event.remoteAddress());
        }
    }

    public Optional<String> lookupDeviceIdForHost(String host) {
        String key = normalizeHost(host);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(deviceIdByHost.get(key));
    }

    public RfidScanEvent pollScan() {
        return scanQueue.poll();
    }

    public int pendingCount() {
        return scanQueue.size();
    }

    public Optional<RfidScanEvent> parseLine(String rawLine, String remoteAddress) {
        if (rawLine == null) {
            return Optional.empty();
        }
        String text = rawLine.trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }

        String host = normalizeHost(remoteAddress);

        if (text.startsWith("UID:")) {
            String cardId = text.substring(4).trim();
            if (cardId.isEmpty()) {
                return Optional.empty();
            }
            String deviceId = deviceIdByHost.getOrDefault(host, "unknown");
            return Optional.of(new RfidScanEvent(deviceId, cardId, remoteAddress, Instant.now()));
        }

        int comma = text.indexOf(',');
        if (comma > 0) {
            String deviceId = text.substring(0, comma).trim();
            String cardId = text.substring(comma + 1).trim();
            if (!deviceId.isEmpty() && !cardId.isEmpty()) {
                return Optional.of(new RfidScanEvent(deviceId, cardId, remoteAddress, Instant.now()));
            }
        }

        // Tek parca kart ID (test)
        if (text.length() >= 4 && text.length() <= 64) {
            String deviceId = deviceIdByHost.getOrDefault(host, "unknown");
            return Optional.of(new RfidScanEvent(deviceId, text, remoteAddress, Instant.now()));
        }

        log.warn("[RFID] Taninmayan satir '{}' from {}", text, remoteAddress);
        return Optional.empty();
    }

    /** {@code /192.168.1.100:1000} veya {@code 192.168.1.100:1000} -> {@code 192.168.1.100} */
    public static String normalizeHost(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return "";
        }
        String s = remoteAddress.trim();
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        if (s.startsWith("[")) {
            int end = s.indexOf(']');
            if (end > 1) {
                return s.substring(1, end);
            }
        }
        int colon = s.lastIndexOf(':');
        if (colon > 0 && s.indexOf(':') == colon) {
            return s.substring(0, colon);
        }
        return s;
    }
}
