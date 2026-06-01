package com.ticket.backend.rfid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Yerel agda RFID uc birim (CH9120 TCP sunucu, varsayilan :1000) arar.
 */
@Component
public class RfidDeviceNetworkScanner {

    private static final Logger log = LoggerFactory.getLogger(RfidDeviceNetworkScanner.class);

    private final ExecutorService pool = Executors.newFixedThreadPool(32, r -> {
        Thread t = new Thread(r, "rfid-scan");
        t.setDaemon(true);
        return t;
    });

    @Value("${device.scan.subnets:192.168.1,192.168.2}")
    private String scanSubnets;

    @Value("${device.scan.port:1000}")
    private int scanPort;

    @Value("${device.scan.host-from:1}")
    private int hostFrom;

    @Value("${device.scan.host-to:254}")
    private int hostTo;

    @Value("${device.scan.timeout-ms:250}")
    private int timeoutMs;

    public List<String> scanConnectedDeviceIps() {
        List<String> prefixes = parseSubnets(scanSubnets);
        if (prefixes.isEmpty()) {
            return List.of();
        }

        List<Future<String>> futures = new ArrayList<>();
        for (String prefix : prefixes) {
            for (int host = hostFrom; host <= hostTo; host++) {
                String ip = prefix + "." + host;
                futures.add(pool.submit(() -> isRfidBridgeReachable(ip) ? ip : null));
            }
        }

        List<String> found = new ArrayList<>();
        for (Future<String> f : futures) {
            try {
                String ip = f.get(timeoutMs + 500L, TimeUnit.MILLISECONDS);
                if (ip != null) {
                    found.add(ip);
                }
            } catch (Exception e) {
                // timeout / cancel — atla
            }
        }
        Collections.sort(found);
        log.info("RFID ag taramasi: {} cihaz bulundu (port {})", found.size(), scanPort);
        return found;
    }

    private boolean isRfidBridgeReachable(String ip) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, scanPort), timeoutMs);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static List<String> parseSubnets(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
