package com.ticket.backend.plc;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * PLC mod / durum register'larinda cihaz basina bit (plcBit 1..N).
 */
@Service
public class PlcBitService {

    private final PlcService plc;

    @Value("${plc.mode-register:42001}")
    private int modeRegister;

    @Value("${plc.state-register:42002}")
    private int stateRegister;

    @Value("${plc.state-started-bit-value:1}")
    private int stateStartedBitValue;

    public PlcBitService(PlcService plc) {
        this.plc = plc;
    }

    public void setModeBit(int plcBit, boolean on) {
        int mask = bitMask(plcBit);
        Integer current = plc.readRegister(modeRegister);
        int value = current != null ? current : 0;
        if (on) {
            value |= mask;
        } else {
            value &= ~mask;
        }
        plc.writeRegister(modeRegister, value);
    }

    public void clearModeBit(int plcBit) {
        setModeBit(plcBit, false);
    }

    public boolean isStateBitActive(int plcBit) {
        Integer state = plc.readRegister(stateRegister);
        if (state == null) {
            return false;
        }
        int mask = bitMask(plcBit);
        if (stateStartedBitValue == 1) {
            return (state & mask) != 0;
        }
        return (state & mask) == mask;
    }

    public boolean isStateBitClear(int plcBit) {
        return !isStateBitActive(plcBit);
    }

    private static int bitMask(int plcBit) {
        if (plcBit < 1 || plcBit > 31) {
            throw new IllegalArgumentException("plcBit must be 1..31, got " + plcBit);
        }
        return 1 << (plcBit - 1);
    }
}
