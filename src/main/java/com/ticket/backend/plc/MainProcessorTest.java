package com.ticket.backend.plc;

import com.ticket.backend.rfid.RfidCardLookupService;
import com.ticket.backend.rfid.RfidScanEvent;
import com.ticket.backend.rfid.RfidScanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Test modu: PLC yok; RFID okumalarinda DB'deki kart + cihaz bilgisini terminale yazar.
 * Calistirma: --spring.profiles.active=rfid-test
 */
@Component
@Profile("rfid-test")
public class MainProcessorTest implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MainProcessorTest.class);

    private final RfidScanService rfidScanService;
    private final RfidCardLookupService rfidCardLookupService;
    private final SystemState systemState;

    public MainProcessorTest(
            RfidScanService rfidScanService,
            RfidCardLookupService rfidCardLookupService,
            SystemState systemState) {
        this.rfidScanService = rfidScanService;
        this.rfidCardLookupService = rfidCardLookupService;
        this.systemState = systemState;
    }

    @Override
    public void run(String... args) {
        Thread loop = new Thread(this::rfidLoop, "rfid-test-loop");
        loop.setDaemon(false);
        loop.start();
    }

    private void rfidLoop() {
        log.info("RFID test modu — kart okumalari DB ile eslestiriliyor (PLC kapali)...");
        System.out.println("=== RFID test: site-config cihaz/operasyon yuklendi. Kart okutun. ===");
        while (!Thread.currentThread().isInterrupted()) {
            RfidScanEvent event = rfidScanService.pollScan();
            if (event != null) {
                systemState.setLastBarcode(event.cardId());
                rfidCardLookupService.lookup(event);
            }
            sleepQuietly(50);
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
