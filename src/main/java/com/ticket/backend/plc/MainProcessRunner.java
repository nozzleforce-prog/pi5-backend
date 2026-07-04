package com.ticket.backend.plc;

import com.ticket.backend.rfid.RfidCardLookupService;
import com.ticket.backend.rfid.RfidQueueOrchestrator;
import com.ticket.backend.rfid.RfidScanEvent;
import com.ticket.backend.rfid.RfidScanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * RFID kuyruk tuketimini baslatir; lookup-only modda sadece DB sorgusu yapar.
 */
@Component
@Profile("!rfid-test & !test")
public class MainProcessRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(MainProcessRunner.class);

    private final PlcService plc;
    private final SystemState sysState;
    private final RfidScanService rfidScanService;
    private final RfidQueueOrchestrator rfidQueueOrchestrator;
    private final RfidCardLookupService rfidCardLookupService;
    private final boolean plcEnabled;
    private final boolean lookupOnly;
    private final int modeRegister;
    private final int alarmRegister;

    public MainProcessRunner(
            PlcService plc,
            SystemState sysState,
            RfidScanService rfidScanService,
            RfidQueueOrchestrator rfidQueueOrchestrator,
            RfidCardLookupService rfidCardLookupService,
            @Value("${plc.enabled:true}") boolean plcEnabled,
            @Value("${rfid.scan.lookup-only:false}") boolean lookupOnly,
            @Value("${plc.mode-register:40001}") int modeRegister,
            @Value("${plc.alarm-register:40003}") int alarmRegister) {
        this.plc = plc;
        this.sysState = sysState;
        this.rfidScanService = rfidScanService;
        this.rfidQueueOrchestrator = rfidQueueOrchestrator;
        this.rfidCardLookupService = rfidCardLookupService;
        this.plcEnabled = plcEnabled;
        this.lookupOnly = lookupOnly;
        this.modeRegister = modeRegister;
        this.alarmRegister = alarmRegister;
    }

    @Override
    public void run(String... args) {
        if (lookupOnly) {
            startLookupOnlyLoop();
        } else {
            rfidQueueOrchestrator.startConsumerLoop();
        }

        if (plcEnabled) {
            startGlobalPlcPolling();
        } else {
            logger.info("PLC devre disi — sadece RFID kuyrugu islenir.");
        }
    }

    private void startLookupOnlyLoop() {
        Thread loop = new Thread(() -> {
            logger.info("RFID lookup-only modu aktif");
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    RfidScanEvent event = rfidScanService.takeScan();
                    rfidCardLookupService.lookup(event);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.warn("Lookup hatasi: {}", e.getMessage());
                }
            }
        }, "rfid-lookup-only");
        loop.setDaemon(true);
        loop.start();
    }

    private void startGlobalPlcPolling() {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Integer alarm = plc.readRegister(alarmRegister);
                if (alarm != null && alarm == 1) {
                    plc.writeRegister(modeRegister, 0);
                    sysState.update(0, null, 1, null);
                }
                sleepQuietly(500);
            }
        }, "plc-alarm-poll");
        t.setDaemon(true);
        t.start();
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
