package com.ticket.backend.plc;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SystemState {
    private final AtomicInteger mode = new AtomicInteger(0);
    private final AtomicInteger state = new AtomicInteger(0);
    private final AtomicInteger alarm = new AtomicInteger(0);
    private String lastBarcode = null;

    public synchronized void update(Integer m, Integer s, Integer a, String b) {
        if (m != null) mode.set(m);
        if (s != null) state.set(s);
        if (a != null) alarm.set(a);
        if (b != null) lastBarcode = b;
    }

    public int getMode() { return mode.get(); }
    public int getState() { return state.get(); }
    public int getAlarm() { return alarm.get(); }
    public synchronized String getLastBarcode() { return lastBarcode; }
    public synchronized void setLastBarcode(String barcode) { this.lastBarcode = barcode; }
    public synchronized void clearBarcode() { this.lastBarcode = null; }
}