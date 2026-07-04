package com.ticket.backend.plc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * GMT Suite holding register'lari: modBilgisi (40001), durumBilgisi (40002), time (40004).
 * Time register tek ve tum cihazlar icin paylasilir — handshake senkronize edilir.
 */
@Service
public class PlcBitService {

    private static final Logger log = LoggerFactory.getLogger(PlcBitService.class);

    private final PlcService plc;
    private final SystemState sysState;
    private final Object handshakeLock = new Object();

    @Value("${plc.mode-register:40001}")
    private int modeRegister;

    @Value("${plc.state-register:40003}")
    private int stateRegister;

    @Value("${plc.time-register:40002}")
    private int timeRegister;

    @Value("${plc.state-started-bit-value:1}")
    private int stateStartedBitValue;

    public PlcBitService(PlcService plc, SystemState sysState) {
        this.plc = plc;
        this.sysState = sysState;
    }

    /**
     * Paylasilan time register + mode bit handshake (sirasiyla mode ON, sonra time yaz).
     * Time register birimi: saniye, cozunurluk 1 (tam saniye).
     */
    public boolean beginHandshake(int plcBit, int durationSeconds) {
        synchronized (handshakeLock) {
            if (durationSeconds < 0) {
                throw new IllegalArgumentException("durationSeconds must be >= 0");
            }
            if (!applyModeBit(plcBit, true)) {
                return false;
            }
            if (!writeTimeRegister(durationSeconds)) {
                applyModeBit(plcBit, false);
                return false;
            }
            log.info("PLC handshake basladi: plcBit={} durationSec={}", plcBit, durationSeconds);
            return true;
        }
    }

    /** Timeout veya iptal: mode bit + time register sifirla. */
    public void resetHandshake(int plcBit) {
        synchronized (handshakeLock) {
            applyModeBit(plcBit, false);
            writeTimeRegister(0);
            log.info("PLC handshake sifirlandi: plcBit={}", plcBit);
        }
    }

    /** Makine durdu: yalnizca mode bit kapat (time sonraki handshake ile ustune yazilir). */
    public void clearModeBit(int plcBit) {
        synchronized (handshakeLock) {
            applyModeBit(plcBit, false);
        }
    }

    public boolean setModeBit(int plcBit, boolean on) {
        synchronized (handshakeLock) {
            return applyModeBit(plcBit, on);
        }
    }

    public boolean isStateBitActive(int plcBit) {
        Integer state = plc.readRegister(stateRegister);
        if (state == null) {
            return false;
        }
        sysState.update(null, state, null, null);
        int mask = bitMask(plcBit);
        if (stateStartedBitValue == 1) {
            return (state & mask) != 0;
        }
        return (state & mask) == mask;
    }

    public boolean isStateBitClear(int plcBit) {
        return !isStateBitActive(plcBit);
    }

    public Integer readModeRegister() {
        return plc.readRegister(modeRegister);
    }

    public Integer readStateRegister() {
        return plc.readRegister(stateRegister);
    }

    public Integer readTimeRegister() {
        return plc.readRegister(timeRegister);
    }

    private boolean writeTimeRegister(int seconds) {
        boolean ok = plc.writeRegister(timeRegister, seconds);
        if (ok) {
            Integer readback = plc.readRegister(timeRegister);
            log.info("PLC time register {} = {} (readback={})", timeRegister, seconds, readback);
        } else {
            log.error("PLC time yazilamadi: register={} value={}", timeRegister, seconds);
        }
        return ok;
    }

    private boolean applyModeBit(int plcBit, boolean on) {
        int mask = bitMask(plcBit);
        Integer current = plc.readRegister(modeRegister);
        int value = current != null ? current : 0;
        int previous = value;
        if (on) {
            value |= mask;
        } else {
            value &= ~mask;
        }
        if (value == previous) {
            log.debug("PLC mode register {} unchanged value={} (plcBit={} on={})",
                    modeRegister, value, plcBit, on);
            sysState.update(value, null, null, null);
            return true;
        }
        boolean ok = plc.writeRegister(modeRegister, value);
        if (ok) {
            Integer readback = plc.readRegister(modeRegister);
            sysState.update(value, null, null, null);
            log.info("PLC mode register {} bit {} -> {} (mask=0x{} value {} -> {} readback={})",
                    modeRegister, plcBit, on ? "ON" : "OFF", Integer.toHexString(mask),
                    previous, value, readback != null ? readback : "?");
        } else {
            log.error("PLC mode yazilamadi: register={} plcBit={} hedefDeger={}", modeRegister, plcBit, value);
        }
        return ok;
    }

    private static int bitMask(int plcBit) {
        if (plcBit < 0 || plcBit > 31) {
            throw new IllegalArgumentException("plcBit must be 0..31, got " + plcBit);
        }
        return 1 << plcBit;
    }
}
