package com.ticket.backend.rfid;

import com.ticket.backend.model.Device;
import com.ticket.backend.model.Operation;
import com.ticket.backend.repository.DeviceRepository;
import com.ticket.backend.service.DeviceService;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RfidScanService {

    private static final Logger log = LoggerFactory.getLogger(RfidScanService.class);

    /** MAKINE:1 KART:ABC123 AD:water */
    private static final Pattern NANO_P4_SCAN = Pattern.compile(
            "MAKINE:(\\d+)\\s+KART:([^\\s]+)(?:\\s+AD:(.+))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NANO_P4_COMBINED_SCAN = Pattern.compile(
            ".*?(?:MAKINE|DEVICE|DEVICE_ID|DEVICEID|CIHAZ|CIHAZ_ID):(\\d+)\\b.*?(?:KART|CARD|UID):([^\\s]+).*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NANO_P4_DEVICE_SELECT = Pattern.compile(
            "(?:MAKINE|DEVICE|DEVICE_ID|DEVICEID|CIHAZ|CIHAZ_ID):(\\d+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CARD_FIELD = Pattern.compile(
            ".*?(?:KART|CARD|UID):([^\\s]+).*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BARE_DEVICE_ID = Pattern.compile("\\d{1,3}");

    private final BlockingQueue<RfidScanEvent> scanQueue = new LinkedBlockingQueue<>();
    private final Map<String, String> deviceIdByHost = new ConcurrentHashMap<>();
    private final Map<String, String> pendingDeviceIdByHost = new ConcurrentHashMap<>();
    private final RfidConnectionRegistry connectionRegistry;
    private final DeviceOperationGate deviceOperationGate;
    private final DeviceRepository deviceRepository;
    private final DeviceService deviceService;

    @Value("${rfid.console.print:true}")
    private boolean consolePrint;

    public RfidScanService(
            RfidConnectionRegistry connectionRegistry,
            DeviceOperationGate deviceOperationGate,
            DeviceRepository deviceRepository,
            DeviceService deviceService) {
        this.connectionRegistry = connectionRegistry;
        this.deviceOperationGate = deviceOperationGate;
        this.deviceRepository = deviceRepository;
        this.deviceService = deviceService;
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
        if (deviceOperationGate.isDeviceRunning(event.deviceId())) {
            log.info("[RFID] Cihaz calisiyor — kuyruga alinmadi: deviceId={} cardId={}",
                    event.deviceId(), event.cardId());
            if (event.readerType() == RfidReaderType.P4_NANO) {
                sendNanoP4Rejected(event.deviceId());
            } else {
                connectionRegistry.sendToDevice(event.deviceId(),
                        RfidDeviceProtocol.busy(event.deviceId()));
            }
            return;
        }
        publishScan(event);
    }

    private void publishScan(RfidScanEvent event) {
        scanQueue.offer(event);
        log.info("[RFID] deviceId={} cardId={} reader={} from {}",
                event.deviceId(), event.cardId(), event.readerType(), event.remoteAddress());
        if (consolePrint) {
            System.out.printf("%n>>> KART ID: %s  (cihaz: %s, %s)  [%s]%n%n",
                    event.cardId(), event.deviceId(), event.readerType(), event.remoteAddress());
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

    public RfidScanEvent takeScan() throws InterruptedException {
        return scanQueue.take();
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

        if (isIgnorableTcpLine(text)) {
            log.debug("[RFID] Ignored TCP line: {}", text);
            return Optional.empty();
        }

        String host = normalizeHost(remoteAddress);

        Optional<RfidScanEvent> combinedNano = parseNanoP4CombinedLine(text, remoteAddress);
        if (combinedNano.isPresent()) {
            return combinedNano;
        }

        if (handleNanoP4DeviceSelect(text, host)) {
            return Optional.empty();
        }

        Optional<RfidScanEvent> nano = parseNanoP4Line(text, remoteAddress);
        if (nano.isPresent()) {
            return nano;
        }

        Optional<RfidScanEvent> selectedNano = parseSelectedNanoP4CardLine(text, host, remoteAddress);
        if (selectedNano.isPresent()) {
            return selectedNano;
        }

        if (text.startsWith("UID:")) {
            String cardId = text.substring(4).trim();
            if (cardId.isEmpty()) {
                return Optional.empty();
            }
            String deviceId = deviceIdByHost.getOrDefault(host, "unknown");
            return Optional.of(new RfidScanEvent(
                    deviceId, cardId, remoteAddress, Instant.now(), RfidReaderType.RP2350));
        }

        int comma = text.indexOf(',');
        if (comma > 0) {
            String deviceId = text.substring(0, comma).trim();
            String cardId = text.substring(comma + 1).trim();
            if (!deviceId.isEmpty() && !cardId.isEmpty()) {
                return Optional.of(new RfidScanEvent(
                        deviceId, cardId, remoteAddress, Instant.now(), RfidReaderType.RP2350));
            }
        }

        if (text.length() >= 4 && text.length() <= 64) {
            String deviceId = deviceIdByHost.getOrDefault(host, "unknown");
            return Optional.of(new RfidScanEvent(
                    deviceId, text, remoteAddress, Instant.now(), RfidReaderType.RP2350));
        }

        log.warn("[RFID] Taninmayan satir '{}' from {}", text, remoteAddress);
        return Optional.empty();
    }

    private Optional<RfidScanEvent> parseNanoP4Line(String text, String remoteAddress) {
        Matcher m = NANO_P4_SCAN.matcher(text);
        if (!m.matches()) {
            return Optional.empty();
        }
        String deviceId = m.group(1).trim();
        String cardId = m.group(2).trim();
        if (deviceId.isEmpty() || cardId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RfidScanEvent(
                deviceId, cardId, remoteAddress, Instant.now(), RfidReaderType.P4_NANO));
    }

    private Optional<RfidScanEvent> parseNanoP4CombinedLine(String text, String remoteAddress) {
        Matcher m = NANO_P4_COMBINED_SCAN.matcher(text);
        if (!m.matches()) {
            return Optional.empty();
        }
        String deviceId = m.group(1).trim();
        String cardId = m.group(2).trim();
        if (deviceId.isEmpty() || cardId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RfidScanEvent(
                deviceId, cardId, remoteAddress, Instant.now(), RfidReaderType.P4_NANO));
    }

    private boolean handleNanoP4DeviceSelect(String text, String host) {
        Optional<String> selectedDeviceId = parseNanoP4SelectedDeviceId(text);
        if (selectedDeviceId.isEmpty()) {
            return false;
        }

        String deviceId = selectedDeviceId.get();
        Optional<Device> deviceOpt = deviceRepository.findByDeviceId(deviceId);
        if (deviceOpt.isEmpty() || !deviceOpt.get().isActive()) {
            pendingDeviceIdByHost.remove(host);
            log.warn("[RFID] P4 device secimi gecersiz: deviceId={}", deviceId);
            connectionRegistry.sendToDevice(deviceId, NanoP4DeviceProtocol.operationLine(0, 0));
            return true;
        }

        Device device = deviceOpt.get();
        try {
            Operation operation = deviceService.resolveOperation(device);
            int operationCode = operation.getOperationCode();
            int fee = Math.max(0, operation.getOperationFee());

            pendingDeviceIdByHost.put(host, deviceId);
            connectionRegistry.setReaderType(deviceId, RfidReaderType.P4_NANO);
            connectionRegistry.sendToDevice(deviceId, NanoP4DeviceProtocol.operationLine(operationCode, fee));
            log.info("[RFID] P4 operasyon bilgisi gonderildi: deviceId={} operation={} fee={}",
                    deviceId, operationCode, fee);
        } catch (Exception e) {
            pendingDeviceIdByHost.remove(host);
            log.warn("[RFID] P4 operasyon bilgisi hazirlanamadi: deviceId={} error={}",
                    deviceId, e.getMessage());
            connectionRegistry.sendToDevice(deviceId, NanoP4DeviceProtocol.operationLine(0, 0));
        }
        return true;
    }

    private Optional<String> parseNanoP4SelectedDeviceId(String text) {
        Matcher m = NANO_P4_DEVICE_SELECT.matcher(text);
        if (m.matches()) {
            return Optional.of(m.group(1).trim());
        }
        if (BARE_DEVICE_ID.matcher(text).matches()
                && deviceRepository.findByDeviceId(text).filter(Device::isActive).isPresent()) {
            return Optional.of(text);
        }
        return Optional.empty();
    }

    private Optional<RfidScanEvent> parseSelectedNanoP4CardLine(
            String text, String host, String remoteAddress) {
        String deviceId = pendingDeviceIdByHost.get(host);
        if (deviceId == null || deviceId.isBlank()) {
            return Optional.empty();
        }

        String cardId = normalizeSelectedCardId(text);
        if (cardId.isEmpty()) {
            return Optional.empty();
        }

        pendingDeviceIdByHost.remove(host);
        return Optional.of(new RfidScanEvent(
                deviceId, cardId, remoteAddress, Instant.now(), RfidReaderType.P4_NANO));
    }

    private static String normalizeSelectedCardId(String text) {
        String cardId = text.trim();
        Matcher cardField = CARD_FIELD.matcher(cardId);
        if (cardField.matches()) {
            return cardField.group(1).trim();
        }
        int firstSpace = cardId.indexOf(' ');
        if (firstSpace > 0) {
            cardId = cardId.substring(0, firstSpace).trim();
        }
        return cardId;
    }

    private static boolean isIgnorableTcpLine(String text) {
        String lower = text.toLowerCase();
        if (lower.startsWith("durum #")) {
            return true;
        }
        if (lower.contains("esp32-p4-nano") || lower.contains("baglandi")) {
            return true;
        }
        if (lower.startsWith("merhaba pc")) {
            return true;
        }
        return false;
    }

    private void sendNanoP4Rejected(String deviceId) {
        connectionRegistry.sendLinesToDevice(
                deviceId,
                NanoP4DeviceProtocol.balanceLine(0),
                NanoP4DeviceProtocol.machineStartedLine(false),
                NanoP4DeviceProtocol.durationLine(0));
    }

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
