package com.ticket.backend.rfid;

import com.ticket.backend.model.Device;
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
 * Tek RFID okuma olayi icin PLC + bilet + RP2350 yanit akisi (cihaz basina thread).
 */
@Service
public class RfidSessionHandler {

    private static final Logger log = LoggerFactory.getLogger(RfidSessionHandler.class);

    private final DeviceRepository deviceRepository;
    private final TicketService ticketService;
    private final PlcBitService plcBitService;
    private final RfidConnectionRegistry connectionRegistry;
    private final boolean plcEnabled;
    private final long machineStartTimeoutMs;
    private final long statePollIntervalMs;
    private final ExecutorService sessionPool;

    public RfidSessionHandler(
            DeviceRepository deviceRepository,
            TicketService ticketService,
            PlcBitService plcBitService,
            RfidConnectionRegistry connectionRegistry,
            @Value("${plc.enabled:true}") boolean plcEnabled,
            @Value("${plc.machine-start-timeout-ms:30000}") long machineStartTimeoutMs,
            @Value("${plc.state-poll-interval-ms:200}") long statePollIntervalMs,
            @Value("${rfid.max-sessions:16}") int maxSessions) {
        this.deviceRepository = deviceRepository;
        this.ticketService = ticketService;
        this.plcBitService = plcBitService;
        this.connectionRegistry = connectionRegistry;
        this.plcEnabled = plcEnabled;
        this.machineStartTimeoutMs = machineStartTimeoutMs;
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

        if (!connectionRegistry.tryBeginSession(deviceId)) {
            log.debug("RFID oturum atlandi (mesgul/kapali): deviceId={} cardId={}", deviceId, cardId);
            connectionRegistry.sendToDevice(deviceId, "BUSY,deviceId=" + deviceId);
            return;
        }

        Optional<Device> deviceOpt = deviceRepository.findByDeviceId(deviceId);
        if (deviceOpt.isEmpty() || !deviceOpt.get().isActive()) {
            failAndRelease(deviceId, "UNKNOWN_DEVICE");
            return;
        }

        Device device = deviceOpt.get();
        int plcBit = device.getPlcBit();

        try {
            if (plcEnabled) {
                plcBitService.setModeBit(plcBit, true);
                log.info("PLC mode bit acildi: deviceId={} plcBit={}", deviceId, plcBit);

                if (!waitForMachineStart(plcBit)) {
                    log.warn("PLC state bit 1 olmadi (timeout): deviceId={}", deviceId);
                    plcBitService.clearModeBit(plcBit);
                    failAndRelease(deviceId, "PLC_TIMEOUT");
                    return;
                }
            }

            TicketUseResult useResult = ticketService.useTicketWithResult(cardId, deviceId);
            if (!useResult.success()) {
                if (plcEnabled) {
                    plcBitService.clearModeBit(plcBit);
                }
                failAndRelease(deviceId, "TICKET_INVALID");
                return;
            }

            String okLine = "OK,deviceId=" + deviceId + ",cardId=" + cardId + ",balance=" + useResult.remainingBalance();
            connectionRegistry.sendToDevice(deviceId, okLine);
            log.info("Bilet kullanildi: {} -> balance={}", deviceId, useResult.remainingBalance());

            if (plcEnabled) {
                waitForMachineStop(plcBit);
                plcBitService.clearModeBit(plcBit);
                log.info("PLC cycle tamamlandi: deviceId={}", deviceId);
            }
        } catch (Exception e) {
            log.error("RFID oturum hata deviceId={}: {}", deviceId, e.getMessage(), e);
            if (plcEnabled) {
                plcBitService.clearModeBit(plcBit);
            }
            failAndRelease(deviceId, "ERROR");
        } finally {
            connectionRegistry.endSession(deviceId);
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

    private void failAndRelease(String deviceId, String reason) {
        connectionRegistry.sendToDevice(deviceId, "FAIL,deviceId=" + deviceId + ",reason=" + reason);
        connectionRegistry.endSession(deviceId);
    }
}
