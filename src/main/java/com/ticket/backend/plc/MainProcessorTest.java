package com.ticket.backend.plc;

import com.ticket.backend.rfid.RfidScanEvent;
import com.ticket.backend.rfid.RfidScanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Test modu: PLC yok; sadece {@link RfidScanService} kuyrugundan kart (barcode) ID okur.
 * Calistirma: --spring.profiles.active=rfid-test
 */
@Component
@Profile("rfid-test")
public class MainProcessorTest implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MainProcessorTest.class);

    private final RfidScanService rfidScanService;
    private final SystemState systemState;

    public MainProcessorTest(RfidScanService rfidScanService, SystemState systemState) {
        this.rfidScanService = rfidScanService;
        this.systemState = systemState;
    }

    @Override
    public void run(String... args) {
        Thread loop = new Thread(this::rfidLoop, "rfid-test-loop");
        loop.setDaemon(false);
        loop.start();
    }

    private void rfidLoop() {
        log.info("RFID test modu — PLC devre disi, kart okumalari dinleniyor...");
        while (!Thread.currentThread().isInterrupted()) {
            RfidScanEvent event = rfidScanService.pollScan();
            if (event != null) {
                onCardScanned(event);
            }
            sleepQuietly(50);
        }
    }

    private void onCardScanned(RfidScanEvent event) {
        systemState.setLastBarcode(event.cardId());
        log.info("[RFID-TEST] islendi cardId={} deviceId={}", event.cardId(), event.deviceId());
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
