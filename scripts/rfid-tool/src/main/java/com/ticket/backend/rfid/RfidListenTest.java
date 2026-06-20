package com.ticket.backend.rfid;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Minimal RFID TCP listener — waits for RP2040/RP2350 client, prints card scans.
 * No Spring Boot. RP2040 sends: deviceId,cardId (e.g. 1,0120286023)
 */
public final class RfidListenTest {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 2000;
        String expectedHost = args.length > 1 ? args[1] : "169.254.179.2";
        String defaultDeviceId = args.length > 2 ? args[2] : "1";

        System.out.println("========================================");
        System.out.println("  RFID connection test (PC listener)");
        System.out.println("  Listen   : 0.0.0.0:" + port);
        System.out.println("  Expected : " + expectedHost + " -> PC:" + port);
        System.out.println("  1) Bu script acik kalsin");
        System.out.println("  2) CONNECTED satirini bekleyin");
        System.out.println("  3) Sonra kart okutun");
        System.out.println("========================================");
        System.out.println();

        try (ServerSocket server = new ServerSocket(port, 4, InetAddress.getByName("0.0.0.0"))) {
            while (true) {
                System.out.println("Waiting for RFID unit to connect...");
                try (Socket client = server.accept()) {
                    String remote = client.getRemoteSocketAddress().toString();
                    System.out.println("CONNECTED: " + remote);
                    System.out.println("Scan a card on the reader. Ctrl+C to exit.");
                    System.out.println();

                    InputStream in = client.getInputStream();
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(in, StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        printScan(line.trim(), remote, defaultDeviceId);
                    }
                    System.out.println("Connection closed: " + remote);
                    System.out.println();
                }
            }
        }
    }

    private static void printScan(String line, String remote, String defaultDeviceId) {
        if (line.isEmpty()) {
            return;
        }
        System.out.println("[RAW] " + line);

        String deviceId = defaultDeviceId;
        String cardId;

        if (line.startsWith("UID:")) {
            cardId = line.substring(4).trim();
        } else {
            int comma = line.indexOf(',');
            if (comma > 0) {
                deviceId = line.substring(0, comma).trim();
                cardId = line.substring(comma + 1).trim();
            } else {
                cardId = line;
            }
        }

        if (cardId.isEmpty()) {
            System.out.println("  (could not parse cardId)");
            System.out.println();
            return;
        }

        System.out.println("  deviceId : " + deviceId);
        System.out.println("  cardId   : " + cardId);
        System.out.println("  from     : " + remote);
        System.out.println("  OK");
        System.out.println();
    }
}
