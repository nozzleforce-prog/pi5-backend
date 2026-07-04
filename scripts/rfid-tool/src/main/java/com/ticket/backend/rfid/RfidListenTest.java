package com.ticket.backend.rfid;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal RFID TCP listener — RP2350 and ESP32-P4-NANO.
 * RP2350: deviceId,cardId or UID:...
 * P4-NANO: MAKINE:N KART:uid; connect/heartbeat lines ignored.
 */
public final class RfidListenTest {

    private static final Pattern NANO_P4_SCAN = Pattern.compile(
            "MAKINE:(\\d+)\\s+KART:([^\\s]+)(?:\\s+AD:(.+))?",
            Pattern.CASE_INSENSITIVE);

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 2000;
        String expectedHost = args.length > 1 ? args[1] : "169.254.179.2";
        String defaultDeviceId = args.length > 2 ? args[2] : "1";

        System.out.println("========================================");
        System.out.println("  RFID connection test (PC listener)");
        System.out.println("  Listen   : 0.0.0.0:" + port);
        System.out.println("  Expected : " + expectedHost + " -> PC:" + port);
        System.out.println("  Protocol: RP2350 + ESP32-P4-NANO");
        System.out.println("========================================");
        System.out.println();

        try (ServerSocket server = new ServerSocket(port, 4, InetAddress.getByName("0.0.0.0"))) {
            while (true) {
                System.out.println("Waiting for RFID unit to connect...");
                try (Socket client = server.accept()) {
                    String remote = client.getRemoteSocketAddress().toString();
                    System.out.println("CONNECTED: " + remote);
                    System.out.println("Press machine button + scan card. Ctrl+C to exit.");
                    System.out.println();

                    InputStream in = client.getInputStream();
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(in, StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        handleLine(line.trim(), remote, defaultDeviceId);
                    }
                    System.out.println("Connection closed: " + remote);
                    System.out.println();
                }
            }
        }
    }

    private static void handleLine(String line, String remote, String defaultDeviceId) {
        if (line.isEmpty()) {
            return;
        }
        System.out.println("[RAW] " + line);

        if (isIgnorableP4Line(line)) {
            printInfo(line, remote);
            return;
        }

        Matcher p4 = NANO_P4_SCAN.matcher(line);
        if (p4.matches()) {
            String deviceId = p4.group(1).trim();
            String cardId = p4.group(2).trim();
            String name = p4.groupCount() >= 3 && p4.group(3) != null ? p4.group(3).trim() : null;
            printScan(deviceId, cardId, name, remote, "P4-NANO");
            return;
        }

        if (line.startsWith("UID:")) {
            String cardId = line.substring(4).trim();
            printScan(defaultDeviceId, cardId, null, remote, "RP2350");
            return;
        }

        int comma = line.indexOf(',');
        if (comma > 0) {
            String deviceId = line.substring(0, comma).trim();
            String cardId = line.substring(comma + 1).trim();
            printScan(deviceId, cardId, null, remote, "RP2350");
            return;
        }

        System.out.println("  (unrecognized line — not a scan event)");
        System.out.println("  from : " + remote);
        System.out.println();
    }

    private static boolean isIgnorableP4Line(String line) {
        String lower = line.toLowerCase();
        return lower.startsWith("durum #")
                || lower.startsWith("merhaba pc")
                || lower.contains("esp32-p4-nano")
                || lower.contains("baglandi");
    }

    private static void printInfo(String line, String remote) {
        String kind = line.toLowerCase().startsWith("durum #") ? "heartbeat" : "connect";
        System.out.println("  type   : " + kind + " (ignored, not enqueued)");
        System.out.println("  from   : " + remote);
        System.out.println("  OK");
        System.out.println();
    }

    private static void printScan(String deviceId, String cardId, String name, String remote, String protocol) {
        if (cardId.isEmpty()) {
            System.out.println("  (could not parse cardId)");
            System.out.println();
            return;
        }
        System.out.println("  protocol : " + protocol);
        System.out.println("  deviceId : " + deviceId);
        System.out.println("  cardId   : " + cardId);
        if (name != null && !name.isEmpty()) {
            System.out.println("  name     : " + name);
        }
        System.out.println("  from     : " + remote);
        System.out.println("  OK — would enqueue scan");
        System.out.println();
    }
}
