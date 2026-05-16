package com.ticket.backend.plc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class MainProcessRunner implements CommandLineRunner {

    @Autowired private PlcService plc;
    @Autowired private SystemState sysState;
    @Autowired
//    private ScannerService scanner;

    private final Logger logger = LoggerFactory.getLogger(MainProcessRunner.class);

    @Override
    public void run(String... args) throws Exception {
//        scanner.startListening();
        startPollingThread();

        logger.info("System Initialized and Running...");

        while (true) {
            int alarm = sysState.getAlarm();
            int state = sysState.getState();
            int mode = sysState.getMode();

            // 1. Alarm Logic
            if (alarm == 1) {
                plc.writeRegister(42001, 0); // MODE_ADDRESS
                sysState.update(0, null, null, null);
                Thread.sleep(1000);
                continue;
            }

            // 2. State 10 (Ticket Usage)
            if (state == 10) {
                String bc = sysState.getLastBarcode();
                if (bc != null) {
                    logger.info("Using barcode: " + bc);
                    // validatorClient.use(bc);
                    sysState.clearBarcode();
                }
                plc.writeRegister(42001, 0);
                plc.writeRegister(42002, 0);
                Thread.sleep(1000);
                continue;
            }

            // 3. New Barcode Processing
            if (mode == 0) {
//                String barcode = scanner.pollBarcode();
//                if (barcode != null) {
//                    processBarcode(barcode);
//                }
            }

            Thread.sleep(100);
        }
    }

    private void processBarcode(String barcode) {
        logger.info("Processing: " + barcode);
        // Add your validation logic here and call handleMode()
    }

    private void startPollingThread() {
        new Thread(() -> {
            while (true) {
                Integer m = plc.readRegister(42001);
                Integer s = plc.readRegister(42002);
                Integer a = plc.readRegister(42003);
                sysState.update(m, s, a, null);
                try { Thread.sleep(500); } catch (InterruptedException e) {}
            }
        }).start();
    }
}
