package com.ticket.backend.plc;

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
@Profile({"!rfid-test", "!test"})
public class MainProcessRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(MainProcessRunner.class);

    private final PlcService plc;
    private final SystemState sysState;
    private final RfidScanService rfidScanService;
    private final RfidSessionHandler rfidSessionHandler;
    private final boolean plcEnabled;

    public MainProcessRunner(
            PlcService plc,
            SystemState sysState,
            RfidScanService rfidScanService,
            RfidSessionHandler rfidSessionHandler,
            @Value("${plc.enabled:true}") boolean plcEnabled) {
        this.plc = plc;
        this.sysState = sysState;
        this.rfidScanService = rfidScanService;
        this.rfidSessionHandler = rfidSessionHandler;
        this.plcEnabled = plcEnabled;
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
        logger.info("RFID dispatch basladi — her kart okumasi ayri oturum thread'i alir.");
        while (!Thread.currentThread().isInterrupted()) {
            RfidScanEvent event = rfidScanService.pollScan();
            if (event != null) {
                rfidSessionHandler.handleScanAsync(event);
            }
            Thread.sleep(20);
        }
    }

    private void startGlobalPlcPolling() {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Integer alarm = plc.readRegister(42003);
                if (alarm != null && alarm == 1) {
                    plc.writeRegister(42001, 0);
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
