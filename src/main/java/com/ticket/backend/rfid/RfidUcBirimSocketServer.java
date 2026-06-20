package com.ticket.backend.rfid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ticket.backend.model.Device;
import com.ticket.backend.repository.DeviceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Arduino/CH9120 RFID uc birimleri ile TCP baglantisi.
 * <ul>
 *   <li>Dinleme: kart PC'ye {@code :2000} baglanirsa</li>
 *   <li>Giden: kart TCP sunucu {@code :1000} ise backend baglanir</li>
 * </ul>
 * Satir: {@code cihazId,kartId} veya {@code UID:kartId}
 */
@Component
@Profile({"!test"})
public class RfidUcBirimSocketServer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RfidUcBirimSocketServer.class);

    private final RfidScanService rfidScanService;
    private final RfidConnectionRegistry connectionRegistry;
    private final DeviceRepository deviceRepository;

    @Value("${rfid.max-connections:16}")
    private int maxConnections;

    private final ExecutorService executor = Executors.newFixedThreadPool(16, r -> {
        Thread t = new Thread(r, "rfid-tcp");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, AtomicInteger> activeByTarget = new ConcurrentHashMap<>();

    @Value("${rfid.tcp.listen-enabled:true}")
    private boolean listenEnabled;

    @Value("${rfid.tcp.listen-port:2000}")
    private int listenPort;

    @Value("${rfid.tcp.unit-connect-enabled:true}")
    private boolean unitConnectEnabled;

    @Value("${rfid.tcp.unit-targets:169.254.179.2:1000}")
    private String unitTargets;

    @Value("${rfid.tcp.reconnect-delay-ms:3000}")
    private long reconnectDelayMs;

    @Value("${rfid.tcp.connect-timeout-ms:8000}")
    private int connectTimeoutMs;

    @Value("${rfid.tcp.unit-device-ids:169.254.179.2=1}")
    private String unitDeviceIds;

    public RfidUcBirimSocketServer(
            RfidScanService rfidScanService,
            RfidConnectionRegistry connectionRegistry,
            DeviceRepository deviceRepository) {
        this.rfidScanService = rfidScanService;
        this.connectionRegistry = connectionRegistry;
        this.deviceRepository = deviceRepository;
    }

    @Override
    public void run(String... args) {
        connectionRegistry.setMaxConnections(maxConnections);
        applyHostDeviceMappings();
        log.info("RFID TCP baslatiliyor — maxBaglanti={} listen={}:{} outbound={}",
                maxConnections, listenEnabled, listenPort, unitConnectEnabled);

        if (listenEnabled) {
            executor.submit(this::runListenerLoop);
        }
        if (unitConnectEnabled) {
            for (String target : parseTargets(unitTargets)) {
                executor.submit(() -> runOutboundClientLoop(target));
            }
        }
    }

    private void applyHostDeviceMappings() {
        if (unitDeviceIds == null || unitDeviceIds.isBlank()) {
            return;
        }
        for (String entry : unitDeviceIds.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("=", 2);
            if (parts.length == 2) {
                rfidScanService.setDeviceIdForHost(parts[0].trim(), parts[1].trim());
            }
        }
    }

    private void runListenerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try (ServerSocket serverSocket = new ServerSocket()) {
                configureServerSocket(serverSocket);
                serverSocket.bind(new InetSocketAddress("0.0.0.0", listenPort), maxConnections);
                log.info("RFID dinleyici hazir — 0.0.0.0:{} (kart baglanabilir)", listenPort);
                System.out.printf("%n=== RFID dinleniyor: port %d (kart -> PC) ===%n%n", listenPort);

                while (!Thread.currentThread().isInterrupted()) {
                    Socket client = serverSocket.accept();
                    configureClientSocket(client);
                    String remote = client.getRemoteSocketAddress().toString();
                    log.info("RFID baglanti (gelen): {}", remote);
                    System.out.println("[RFID] Baglanti (gelen): " + remote);
                    executor.submit(() -> handleConnection(client, remote, "inbound"));
                }
            } catch (IOException e) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                log.error("RFID dinleyici hata ({} ms sonra tekrar): {}", reconnectDelayMs, e.getMessage());
                sleepQuietly(reconnectDelayMs);
            }
        }
    }

    private void runOutboundClientLoop(String target) {
        String host = splitHostPort(target)[0];
        activeByTarget.putIfAbsent(target, new AtomicInteger(0));

        while (!Thread.currentThread().isInterrupted()) {
            try (Socket socket = new Socket()) {
                configureClientSocket(socket);
                socket.connect(new InetSocketAddress(host, Integer.parseInt(splitHostPort(target)[1])), connectTimeoutMs);
                String remote = socket.getRemoteSocketAddress().toString();
                log.info("RFID baglanti (giden): {} -> {}", target, remote);
                System.out.println("[RFID] Baglanti (giden): " + target);
                handleConnection(socket, remote, target);
            } catch (IOException e) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                log.debug("RFID {} baglanti yok: {}", target, e.getMessage());
            }
            sleepQuietly(reconnectDelayMs);
        }
    }

    private void handleConnection(Socket socket, String remoteLabel, String targetKey) {
        AtomicInteger active = activeByTarget.computeIfAbsent(targetKey, k -> new AtomicInteger(0));
        active.incrementAndGet();
        String host = RfidScanService.normalizeHost(remoteLabel);
        String deviceId = resolveDeviceIdForHost(host);
        PrintWriter writer;
        try {
            writer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        } catch (IOException e) {
            active.decrementAndGet();
            return;
        }

        final String[] boundDeviceId = {deviceId};
        if (boundDeviceId[0] != null) {
            connectionRegistry.register(boundDeviceId[0], host, socket, writer);
        }

        try {
            socket.setSoTimeout(0);
            InputStream in = socket.getInputStream();
            RfidTcpLineReader reader = new RfidTcpLineReader();
            reader.readLoop(in, line -> rfidScanService.parseLine(line, remoteLabel).ifPresent(event -> {
                if (boundDeviceId[0] == null && !"unknown".equals(event.deviceId())) {
                    boundDeviceId[0] = event.deviceId();
                    connectionRegistry.register(boundDeviceId[0], host, socket, writer);
                }
                rfidScanService.enqueueScan(event);
            }));
        } catch (IOException e) {
            log.debug("RFID okuma bitti {}: {}", remoteLabel, e.getMessage());
        } finally {
            if (boundDeviceId[0] != null) {
                connectionRegistry.unregister(boundDeviceId[0]);
            }
            active.decrementAndGet();
            log.info("RFID baglanti kapandi: {}", remoteLabel);
        }
    }

    private String resolveDeviceIdForHost(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }
        Optional<String> fromDb = deviceRepository.findByDeviceIp(host).stream()
                .filter(Device::isActive)
                .findFirst()
                .or(() -> deviceRepository.findByDeviceIp(host).stream().findFirst())
                .map(d -> {
                    connectionRegistry.bindHostToDeviceId(host, d.getDeviceId());
                    rfidScanService.setDeviceIdForHost(host, d.getDeviceId());
                    return d.getDeviceId();
                });
        if (fromDb.isPresent()) {
            return fromDb.get();
        }
        return rfidScanService.lookupDeviceIdForHost(host).orElse(null);
    }

    private static void configureServerSocket(ServerSocket socket) throws IOException {
        socket.setReuseAddress(true);
    }

    private static void configureClientSocket(Socket socket) throws IOException {
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        socket.setSoTimeout(0);
    }

    private static List<String> parseTargets(String targets) {
        if (targets == null || targets.isBlank()) {
            return List.of();
        }
        return Arrays.stream(targets.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String[] splitHostPort(String target) {
        String trimmed = target.trim();
        int colon = trimmed.lastIndexOf(':');
        if (colon <= 0 || colon == trimmed.length() - 1) {
            throw new IllegalArgumentException("Gecersiz hedef (host:port): " + target);
        }
        return new String[]{
                trimmed.substring(0, colon),
                trimmed.substring(colon + 1)
        };
    }

    private static void sleepQuietly(long ms) {
        try {
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
