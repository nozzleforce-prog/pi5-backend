package com.ticket.backend.plc;

import com.ticket.backend.rfid.RfidCardLookupService;
import com.ticket.backend.rfid.RfidConnectionRegistry;
import com.ticket.backend.rfid.RfidScanEvent;
import com.ticket.backend.rfid.RfidScanService;
import com.ticket.backend.rfid.RfidSessionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Surekli RFID kuyrugunu dinler; her okuma icin {@link RfidSessionHandler} ayri thread baslatir.
 * Eszamanli oturum sayisi: {@code rfid.max-sessions} (varsayilan 16, en az 8 RP2350 icin yeterli).
 */
@Component
@Profile("!rfid-test & !test")
public class MainProcessRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(MainProcessRunner.class);

    private final PlcService plc;
    private final SystemState sysState;
    private final RfidScanService rfidScanService;
    private final RfidSessionHandler rfidSessionHandler;
    private final RfidCardLookupService rfidCardLookupService;
    private final RfidConnectionRegistry connectionRegistry;
    private final boolean plcEnabled;
    private final boolean lookupOnly;
    private final int modeRegister;
    private final int alarmRegister;

    public MainProcessRunner(
            PlcService plc,
            SystemState sysState,
            RfidScanService rfidScanService,
            RfidSessionHandler rfidSessionHandler,
            RfidCardLookupService rfidCardLookupService,
            RfidConnectionRegistry connectionRegistry,
            @Value("${plc.enabled:true}") boolean plcEnabled,
            @Value("${rfid.scan.lookup-only:false}") boolean lookupOnly,
            @Value("${plc.mode-register:40001}") int modeRegister,
            @Value("${plc.alarm-register:40003}") int alarmRegister) {
        this.plc = plc;
        this.sysState = sysState;
        this.rfidScanService = rfidScanService;
        this.rfidSessionHandler = rfidSessionHandler;
        this.rfidCardLookupService = rfidCardLookupService;
        this.connectionRegistry = connectionRegistry;
        this.plcEnabled = plcEnabled;
        this.lookupOnly = lookupOnly;
        this.modeRegister = modeRegister;
        this.alarmRegister = alarmRegister;
    }

    @Override
    public void run(String... args) {
        Thread loop = new Thread(() -> {
            try {
                runRfidDispatchLoop();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "main-process-rfid");
        loop.setDaemon(true);
        loop.start();

        if (plcEnabled) {
            startGlobalPlcPolling();
        } else {
            logger.info("PLC devre disi — sadece RFID oturumlari islenir.");
        }
    }

    /**
     * RFID okumalari her zaman dinlenir (cihaz basina ayri thread).
     */
    private void runRfidDispatchLoop() throws InterruptedException {
        logger.info("RFID dispatch basladi — PLC={}, lookupOnly={}", plcEnabled, lookupOnly);
        while (!Thread.currentThread().isInterrupted()) {
            RfidScanEvent event = rfidScanService.pollScan();
            if (event != null && connectionRegistry.isAcceptingScans(event.deviceId())) {
                if (lookupOnly) {
                    rfidCardLookupService.lookup(event);
                } else {
                    rfidSessionHandler.handleScanAsync(event);
                }
            }
            Thread.sleep(20);
        }
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
