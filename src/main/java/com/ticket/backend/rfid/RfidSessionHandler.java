package com.ticket.backend.rfid;

import com.ticket.backend.model.Device;
import com.ticket.backend.model.TicketStatus;
import com.ticket.backend.plc.PlcBitService;
import com.ticket.backend.repository.DeviceRepository;
import com.ticket.backend.service.TicketService;
import com.ticket.backend.service.TicketUseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * RFID okuma + PLC mode/state (40001/40002) + bilet kullanimi.
 * Akis: DB eslesmesi -> mode bit 1 -> state=1 bekle -> useTicket -> state sifirlanana kadar bekle -> mode temizle.
 */
@Service
public class RfidSessionHandler {

    private static final Logger log = LoggerFactory.getLogger(RfidSessionHandler.class);

    private final DeviceRepository deviceRepository;
    private final TicketService ticketService;
    private final PlcBitService plcBitService;
    private final RfidConnectionRegistry connectionRegistry;
    private final RfidCardLookupService rfidCardLookupService;
    private final boolean plcEnabled;
    private final long machineStartTimeoutMs;
    private final long scanResumeDelayMs;
    private final long statePollIntervalMs;
    private final ExecutorService sessionPool;

    public RfidSessionHandler(
            DeviceRepository deviceRepository,
            TicketService ticketService,
            PlcBitService plcBitService,
            RfidConnectionRegistry connectionRegistry,
            RfidCardLookupService rfidCardLookupService,
            @Value("${plc.enabled:true}") boolean plcEnabled,
            @Value("${plc.machine-start-timeout-ms:5000}") long machineStartTimeoutMs,
            @Value("${plc.scan-resume-delay-ms:5000}") long scanResumeDelayMs,
            @Value("${plc.state-poll-interval-ms:200}") long statePollIntervalMs,
            @Value("${rfid.max-sessions:16}") int maxSessions) {
        this.deviceRepository = deviceRepository;
        this.ticketService = ticketService;
        this.plcBitService = plcBitService;
        this.connectionRegistry = connectionRegistry;
        this.rfidCardLookupService = rfidCardLookupService;
        this.plcEnabled = plcEnabled;
        this.machineStartTimeoutMs = machineStartTimeoutMs;
        this.scanResumeDelayMs = scanResumeDelayMs;
        this.statePollIntervalMs = statePollIntervalMs;
        this.sessionPool = Executors.newFixedThreadPool(maxSessions, r -> {
            Thread t = new Thread(r, "rfid-session");
            t.setDaemon(true);
            return t;
        });
        connectionRegistry.setMaxConnections(maxSessions);
    }

    public void handleScanAsync(RfidScanEvent event) {
        sessionPool.submit(() -> processSession(event));
    }

    private void processSession(RfidScanEvent event) {
        String deviceId = event.deviceId();
        String cardId = event.cardId();
        boolean delayScanResume = false;

        if (!connectionRegistry.tryBeginSession(deviceId)) {
            log.debug("RFID oturum atlandi (mesgul/kapali): deviceId={} cardId={}", deviceId, cardId);
            connectionRegistry.sendToDevice(deviceId, "BUSY,deviceId=" + deviceId);
            return;
        }

        Optional<Device> deviceOpt = deviceRepository.findByDeviceId(deviceId);
        if (deviceOpt.isEmpty() || !deviceOpt.get().isActive()) {
            fail(deviceId, "UNKNOWN_DEVICE");
            connectionRegistry.endSession(deviceId);
            return;
        }

        Device device = deviceOpt.get();
        int plcBit = device.getPlcBit();
        log.info("RFID cihaz eslesti: deviceId={} plcBit={} readerIp={}", deviceId, plcBit, device.getDeviceIp());

        rfidCardLookupService.lookup(event);

        if (ticketService.validateTicket(cardId, deviceId) != TicketStatus.ACTIVE) {
            log.warn("Gecersiz bilet veya yetersiz bakiye: cardId={} deviceId={}", cardId, deviceId);
            fail(deviceId, "TICKET_INVALID");
            connectionRegistry.endSession(deviceId);
            return;
        }

        try {
            if (plcEnabled) {
                if (!plcBitService.setModeBit(plcBit, true)) {
                    log.error("PLC mode yazilamadi: deviceId={} plcBit={} register=40001", deviceId, plcBit);
                    fail(deviceId, "PLC_WRITE_FAILED");
                    connectionRegistry.endSession(deviceId);
                    return;
                }
                log.info("PLC mode bit set: deviceId={} plcBit={}", deviceId, plcBit);

                if (!waitForMachineStart(plcBit)) {
                    log.warn("PLC state bit aktif olmadi (timeout {} ms): deviceId={} plcBit={}",
                            machineStartTimeoutMs, deviceId, plcBit);
                    plcBitService.clearModeBit(plcBit);
                    fail(deviceId, "PLC_TIMEOUT");
                    delayScanResume = true;
                    return;
                }

                log.info("PLC state aktif — makine basladi: deviceId={} plcBit={}", deviceId, plcBit);
            }

            TicketUseResult useResult = ticketService.useTicketWithResult(cardId, deviceId);
            if (!useResult.success()) {
                if (plcEnabled) {
                    plcBitService.clearModeBit(plcBit);
                }
                fail(deviceId, "TICKET_INVALID");
                return;
            }

            String okLine = "OK,deviceId=" + deviceId + ",cardId=" + cardId + ",balance=" + useResult.remainingBalance();
            connectionRegistry.sendToDevice(deviceId, okLine);
            log.info("Bilet kullanildi: deviceId={} cardId={} balance={}", deviceId, cardId, useResult.remainingBalance());

            if (plcEnabled) {
                waitForMachineStop(plcBit);
                plcBitService.clearModeBit(plcBit);
                log.info("PLC cycle tamamlandi (state sifirlandi): deviceId={}", deviceId);
            }
        } catch (Exception e) {
            log.error("RFID oturum hata deviceId={}: {}", deviceId, e.getMessage(), e);
            if (plcEnabled) {
                plcBitService.clearModeBit(plcBit);
            }
            fail(deviceId, "ERROR");
        } finally {
            if (delayScanResume) {
                connectionRegistry.endSessionWithScanDelay(deviceId, scanResumeDelayMs);
            } else {
                connectionRegistry.endSession(deviceId);
            }
        }
    }

    private boolean waitForMachineStart(int plcBit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + machineStartTimeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (plcBitService.isStateBitActive(plcBit)) {
                return true;
            }
            Thread.sleep(statePollIntervalMs);
        }
        return false;
    }

    private void waitForMachineStop(int plcBit) throws InterruptedException {
        while (plcBitService.isStateBitActive(plcBit)) {
            Thread.sleep(statePollIntervalMs);
        }
    }

    private void fail(String deviceId, String reason) {
        connectionRegistry.sendToDevice(deviceId, "FAIL,deviceId=" + deviceId + ",reason=" + reason);
    }
}
