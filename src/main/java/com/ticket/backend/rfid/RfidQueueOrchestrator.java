package com.ticket.backend.rfid;

import com.ticket.backend.model.Device;
import com.ticket.backend.model.Operation;
import com.ticket.backend.model.TicketStatus;
import com.ticket.backend.plc.PlcBitService;
import com.ticket.backend.repository.DeviceRepository;
import com.ticket.backend.service.DeviceService;
import com.ticket.backend.service.TicketService;
import com.ticket.backend.service.TicketUseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * RFID okuma kuyrugu — paylasilan PLC time register icin senkronize handshake.
 * <p>
 * Akis:
 * <ol>
 *   <li>Kuyruktan okuma al (FIFO)</li>
 *   <li>Handshake kilidi al (tek time register)</li>
 *   <li>Mode bit ON → time register yaz → state=1 bekle (5s timeout)</li>
 *   <li>State=1: bakiye dus, START mesaji, kilidi birak — sonraki kuyruk elemani handshake yapabilir</li>
 *   <li>Arka planda state=0 bekle, mode temizle, STOP mesaji</li>
 * </ol>
 */
@Service
public class RfidQueueOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RfidQueueOrchestrator.class);

    private final RfidScanService rfidScanService;
    private final RfidConnectionRegistry connectionRegistry;
    private final RfidCardLookupService rfidCardLookupService;
    private final DeviceRepository deviceRepository;
    private final DeviceService deviceService;
    private final TicketService ticketService;
    private final PlcBitService plcBitService;
    private final DeviceOperationGate deviceOperationGate;

    private final boolean plcEnabled;
    private final long machineStartTimeoutMs;
    private final long statePollIntervalMs;

    /** Paylasilan time register — ayni anda yalnizca bir handshake. */
    private final Semaphore handshakeLock = new Semaphore(1);
    private final ExecutorService runningPool;

    public RfidQueueOrchestrator(
            RfidScanService rfidScanService,
            RfidConnectionRegistry connectionRegistry,
            RfidCardLookupService rfidCardLookupService,
            DeviceRepository deviceRepository,
            DeviceService deviceService,
            TicketService ticketService,
            PlcBitService plcBitService,
            DeviceOperationGate deviceOperationGate,
            @Value("${plc.enabled:true}") boolean plcEnabled,
            @Value("${plc.machine-start-timeout-ms:5000}") long machineStartTimeoutMs,
            @Value("${plc.state-poll-interval-ms:200}") long statePollIntervalMs,
            @Value("${rfid.max-sessions:16}") int maxRunningSessions) {
        this.rfidScanService = rfidScanService;
        this.connectionRegistry = connectionRegistry;
        this.rfidCardLookupService = rfidCardLookupService;
        this.deviceRepository = deviceRepository;
        this.deviceService = deviceService;
        this.ticketService = ticketService;
        this.plcBitService = plcBitService;
        this.deviceOperationGate = deviceOperationGate;
        this.plcEnabled = plcEnabled;
        this.machineStartTimeoutMs = machineStartTimeoutMs;
        this.statePollIntervalMs = statePollIntervalMs;
        this.runningPool = Executors.newFixedThreadPool(maxRunningSessions, r -> {
            Thread t = new Thread(r, "rfid-running");
            t.setDaemon(true);
            return t;
        });
    }

    public void startConsumerLoop() {
        Thread consumer = new Thread(this::runConsumerLoop, "rfid-queue-consumer");
        consumer.setDaemon(true);
        consumer.start();
        log.info("RFID kuyruk tuketici basladi — PLC={}, handshake=serial, running=parallel", plcEnabled);
    }

    private void runConsumerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                RfidScanEvent event = rfidScanService.takeScan();
                processHandshakePhase(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("RFID kuyruk isleme hatasi: {}", e.getMessage(), e);
            }
        }
    }

    private void processHandshakePhase(RfidScanEvent event) throws InterruptedException {
        String deviceId = event.deviceId();
        String cardId = event.cardId();

        if (deviceOperationGate.isDeviceRunning(deviceId)) {
            log.info("Cihaz hala calisiyor — kuyruk elemani atlandi: deviceId={} cardId={}", deviceId, cardId);
            sendBusy(deviceId, event.readerType());
            return;
        }

        Optional<Device> deviceOpt = deviceRepository.findByDeviceId(deviceId);
        if (deviceOpt.isEmpty() || !deviceOpt.get().isActive()) {
            log.warn("Bilinmeyen veya pasif cihaz: deviceId={} cardId={}", deviceId, cardId);
            sendFail(deviceId, event.readerType(), "UNKNOWN_DEVICE");
            return;
        }

        Device device = deviceOpt.get();
        int plcBit = device.getPlcBit();
        Operation operation = deviceService.resolveOperation(device);
        int durationSeconds = deviceService.operationDurationSecondsFor(device);
        RfidReaderType readerType = connectionRegistry.resolveReaderType(
                deviceId, event.readerType());
        if (readerType == RfidReaderType.P4_NANO) {
            connectionRegistry.setReaderType(deviceId, RfidReaderType.P4_NANO);
        }

        rfidCardLookupService.lookup(event);

        if (ticketService.validateTicket(cardId, deviceId) != TicketStatus.ACTIVE) {
            log.warn("Gecersiz bilet: cardId={} deviceId={}", cardId, deviceId);
            sendPaymentFailure(deviceId, readerType, 0);
            return;
        }

        handshakeLock.acquire();
        try {
            if (plcEnabled) {
                if (!plcBitService.beginHandshake(plcBit, durationSeconds)) {
                    sendFail(deviceId, readerType, "PLC_WRITE_FAILED");
                    return;
                }

                if (!waitForMachineStart(plcBit)) {
                    log.warn("PLC state timeout: deviceId={} plcBit={} ({} ms)",
                            deviceId, plcBit, machineStartTimeoutMs);
                    plcBitService.resetHandshake(plcBit);
                    sendFail(deviceId, readerType, "PLC_TIMEOUT");
                    return;
                }

                log.info("PLC state=1 — zaman iletildi, makine basladi: deviceId={} plcBit={}", deviceId, plcBit);
                deviceOperationGate.markRunning(deviceId);
            }

            TicketUseResult useResult = ticketService.useTicketWithResult(cardId, deviceId);
            if (!useResult.success()) {
                if (plcEnabled) {
                    plcBitService.resetHandshake(plcBit);
                    runningPool.submit(() -> waitForPlcIdleAndRelease(deviceId, plcBit));
                }
                sendPaymentFailure(deviceId, readerType, 0);
                return;
            }

            sendPaymentSuccess(deviceId, cardId, readerType, durationSeconds,
                    useResult.remainingBalance(), operation.getName());
            log.info("Odeme onaylandi: deviceId={} cardId={} reader={} durationSec={} balance={}",
                    deviceId, cardId, readerType, durationSeconds, useResult.remainingBalance());

            if (plcEnabled) {
                runningPool.submit(() -> waitForMachineStopAndCleanup(
                        deviceId, cardId, plcBit, useResult.remainingBalance(), readerType));
            } else if (readerType == RfidReaderType.RP2350) {
                connectionRegistry.sendToDevice(deviceId,
                        RfidDeviceProtocol.ok(deviceId, cardId, useResult.remainingBalance()));
            }
        } finally {
            handshakeLock.release();
        }
    }

    private void waitForMachineStopAndCleanup(
            String deviceId, String cardId, int plcBit, int lastBalance, RfidReaderType readerType) {
        try {
            waitUntilStateClear(plcBit);
            plcBitService.clearModeBit(plcBit);
            sendMachineDone(deviceId, cardId, readerType, lastBalance);
            log.info("Makine durdu: deviceId={} plcBit={} reader={}", deviceId, plcBit, readerType);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Running phase interrupted: deviceId={}", deviceId);
        } catch (Exception e) {
            log.error("Running phase hata deviceId={}: {}", deviceId, e.getMessage(), e);
            plcBitService.clearModeBit(plcBit);
        } finally {
            deviceOperationGate.markIdle(deviceId);
        }
    }

    /** Bilet hatasi sonrasi PLC state=0 olana kadar cihazi mesgul tut. */
    private void waitForPlcIdleAndRelease(String deviceId, int plcBit) {
        try {
            waitUntilStateClear(plcBit);
            plcBitService.clearModeBit(plcBit);
            log.info("PLC state sifirlandi (bilet hatasi sonrasi): deviceId={} plcBit={}", deviceId, plcBit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("PLC idle bekleme hatasi deviceId={}: {}", deviceId, e.getMessage(), e);
            plcBitService.clearModeBit(plcBit);
        } finally {
            deviceOperationGate.markIdle(deviceId);
        }
    }

    private void waitUntilStateClear(int plcBit) throws InterruptedException {
        while (plcBitService.isStateBitActive(plcBit)) {
            Thread.sleep(statePollIntervalMs);
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

    private void sendPaymentSuccess(
            String deviceId,
            String cardId,
            RfidReaderType readerType,
            int durationSeconds,
            int balance,
            String operationName) {
        if (readerType == RfidReaderType.P4_NANO) {
            connectionRegistry.sendLinesToDevice(
                    deviceId,
                    NanoP4DeviceProtocol.balanceLine(balance),
                    NanoP4DeviceProtocol.machineStartedLine(true),
                    NanoP4DeviceProtocol.durationLine(durationSeconds));
        } else {
            connectionRegistry.sendToDevice(deviceId, RfidDeviceProtocol.start(
                    deviceId, cardId, durationSeconds, balance, operationName));
        }
    }

    private void sendPaymentFailure(String deviceId, RfidReaderType readerType, int balance) {
        if (readerType == RfidReaderType.P4_NANO) {
            connectionRegistry.sendLinesToDevice(
                    deviceId,
                    NanoP4DeviceProtocol.balanceLine(balance),
                    NanoP4DeviceProtocol.machineStartedLine(false),
                    NanoP4DeviceProtocol.durationLine(0));
        } else {
            sendFail(deviceId, readerType, "TICKET_INVALID");
        }
    }

    private void sendMachineDone(String deviceId, String cardId, RfidReaderType readerType, int balance) {
        if (readerType == RfidReaderType.P4_NANO) {
            connectionRegistry.sendToDevice(deviceId, NanoP4DeviceProtocol.machineDoneLine());
        } else {
            connectionRegistry.sendToDevice(deviceId,
                    RfidDeviceProtocol.stop(deviceId, cardId, balance));
        }
    }

    private void sendFail(String deviceId, RfidReaderType readerType, String reason) {
        if (readerType == RfidReaderType.P4_NANO) {
            sendPaymentFailure(deviceId, readerType, 0);
        } else {
            connectionRegistry.sendToDevice(deviceId, RfidDeviceProtocol.fail(deviceId, reason));
        }
    }

    private void sendBusy(String deviceId, RfidReaderType readerType) {
        if (readerType == RfidReaderType.P4_NANO) {
            sendPaymentFailure(deviceId, readerType, 0);
        } else {
            connectionRegistry.sendToDevice(deviceId, RfidDeviceProtocol.busy(deviceId));
        }
    }
}
