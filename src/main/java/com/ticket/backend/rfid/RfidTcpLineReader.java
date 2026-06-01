package com.ticket.backend.rfid;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

/**
 * TCP akisindan satir satir okur ({@code \n} / {@code \r\n}).
 * Arduino/CH9120 bazen parcali gonderir; {@link java.io.BufferedReader#readLine()} yeterli olur
 * ama ham buffer ile kopmalarda daha guvenilir.
 */
final class RfidTcpLineReader {

    private final byte[] buf = new byte[4096];
    private final StringBuilder line = new StringBuilder(128);

    void readLoop(InputStream in, Consumer<String> onLine) throws IOException {
        int n;
        while ((n = in.read(buf)) != -1) {
            for (int i = 0; i < n; i++) {
                char c = (char) (buf[i] & 0xFF);
                if (c == '\n' || c == '\r') {
                    emitLine(onLine);
                } else if (c >= 32 && c < 127) {
                    line.append(c);
                    if (line.length() > 256) {
                        line.setLength(0);
                    }
                }
            }
        }
        emitLine(onLine);
    }

    private void emitLine(Consumer<String> onLine) {
        if (line.isEmpty()) {
            return;
        }
        String text = line.toString().trim();
        line.setLength(0);
        if (!text.isEmpty()) {
            onLine.accept(text);
        }
    }

}
