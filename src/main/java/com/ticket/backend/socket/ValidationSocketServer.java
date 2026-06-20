package com.ticket.backend.socket;

import com.ticket.backend.model.TicketStatus;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import com.ticket.backend.service.TicketService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

@Component
public class ValidationSocketServer implements CommandLineRunner {
    private final TicketService ticketService;

    public ValidationSocketServer(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Override
    public void run(String... args) {
        Thread t = new Thread(this::listenLoop, "validation-socket");
        t.setDaemon(true);
        t.start();
    }

    private void listenLoop() {
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress("0.0.0.0", 5000));
            System.out.println("Validation Socket Server listening on port 5000");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String input = in.readLine();

            if (input.startsWith("USE:")) {
                String[] parts = input.substring(4).split(",", 2);
                String cardId = parts[0].trim();
                String deviceId = parts.length > 1 ? parts[1].trim() : "";
                String result = ticketService.useTicket(cardId, deviceId);
                out.println(result);
                System.out.println("[USAGE] card=" + cardId + " device=" + deviceId + " → " + result);
            } else {
                String[] parts = input.split(",", 2);
                String cardId = parts[0].trim();
                String deviceId = parts.length > 1 ? parts[1].trim() : "";
                TicketStatus status = ticketService.validateTicket(cardId, deviceId);
                out.println(status.name());
                System.out.println("[VALIDATION] card=" + cardId + " device=" + deviceId + " → " + status);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
