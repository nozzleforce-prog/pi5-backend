package com.ticket.backend.socket;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

@Component
public class LogSocketServer implements CommandLineRunner {

    @Override
    public void run(String... args) {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(5001)) {
                System.out.println("Log Socket Server listening on port 5001");

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(() -> handleLogClient(clientSocket)).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleLogClient(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String logLine;
            while ((logLine = in.readLine()) != null) {
                System.out.println("[LOG] " + logLine);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

