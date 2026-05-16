package com.ticket.backend.service;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.ticket.backend.plc.ScanEvent;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class ScannerService {
    private final BlockingQueue<ScanEvent> eventQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> barcodeQueue = new LinkedBlockingQueue<>();
    private final List<SerialPort> activePorts = new ArrayList<>();
    private final Logger logger = LoggerFactory.getLogger(ScannerService.class);

    @PostConstruct
    public void init() {
        startMultiPortListener();
    }

    public void startMultiPortListener() {
        // Find all potential Arduino/RP2040 ports
        SerialPort[] ports = SerialPort.getCommPorts();

        for (SerialPort port : ports) {
            // Filter by description or hardware ID if needed to avoid non-scanner devices
            if (port.getDescriptivePortName().contains("Arduino") || port.getDescriptivePortName().contains("RP2040")) {
                setupPort(port);
            }
        }
    }

    private void setupPort(SerialPort port) {
        port.setBaudRate(115200);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);

        if (port.openPort()) {
            logger.info("Successfully opened port: " + port.getSystemPortName());
            activePorts.add(port);

            // Add an asynchronous listener to this specific port
            port.addDataListener(new SerialPortDataListener() {
                private String buffer = "";

                @Override
                public int getListeningEvents() { return SerialPort.LISTENING_EVENT_DATA_AVAILABLE; }

                @Override
                public void serialEvent(SerialPortEvent event) {
                    if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) return;

                    byte[] newData = new byte[port.bytesAvailable()];
                    port.readBytes(newData, newData.length);

                    // Accumulate string data until a newline (barcode completion)
                    buffer += new String(newData);
                    if (buffer.contains("\n")) {
                        String[] codes = buffer.split("\n");
                        for (int i = 0; i < codes.length - 1; i++) {
                            String code = codes[i].trim();
                            if (!code.isEmpty()) {
                                barcodeQueue.offer(code); // Push to shared queue
                                logger.info("Scanner [{}]: Received {}", port.getSystemPortName(), code);
                            }
                        }
                        buffer = codes[codes.length - 1]; // Keep partial data
                    }
                }
            });
        } else {
            logger.error("Failed to open port: " + port.getSystemPortName());
        }
    }

    private void handleIncomingData(SerialPort port, String rawData) {
        try {
            // Expecting format from Arduino: "12345678,1"
            String[] parts = rawData.trim().split(",");
            if (parts.length == 2) {
                String barcode = parts[0];
                int op = Integer.parseInt(parts[1]);
                String portName = port.getSystemPortName(); // e.g., /dev/ttyACM0

                eventQueue.offer(new ScanEvent(portName, barcode, op));
            }
        } catch (Exception e) {
            // Log malformed data
        }
    }

    public ScanEvent pollEvent() {
        return eventQueue.poll();
    }

    /**
     * The main logic still uses this exact method.
     * It doesn't matter which RP2040 sent the code; it comes out here.
     */
    public String pollBarcode() {
        return barcodeQueue.poll();
    }
}