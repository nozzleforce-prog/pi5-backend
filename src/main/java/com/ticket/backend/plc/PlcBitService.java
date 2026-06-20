package com.ticket.backend.plc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * GMT Suite holding register'lari: modBilgisi (40001), durumBilgisi (40002).
 * Her cihaz {@code site-config.yml} icindeki {@code plc-bit} ile mod register'inda bir bit kullanir.
 */
@Service
public class PlcBitService {

    private static final Logger log = LoggerFactory.getLogger(PlcBitService.class);

    private final PlcService plc;
    private final SystemState sysState;

    @Value("${plc.mode-register:40001}")
    private int modeRegister;

    @Value("${plc.state-register:40002}")
    private int stateRegister;

    @Value("${plc.state-started-bit-value:1}")
    private int stateStartedBitValue;

    public PlcBitService(PlcService plc, SystemState sysState) {
        this.plc = plc;
        this.sysState = sysState;
    }

    /**
     * Mod register'inda (40001) cihazin plcBit indeksini ac/kapat (read-modify-write).
     *
     * @return true if Modbus write succeeded
     */
    public boolean setModeBit(int plcBit, boolean on) {
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

    public void clearModeBit(int plcBit) {
        setModeBit(plcBit, false);
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

    private static int bitMask(int plcBit) {
        if (plcBit < 0 || plcBit > 31) {
            throw new IllegalArgumentException("plcBit must be 0..31, got " + plcBit);
        }
        return 1 << plcBit;
    }
}
