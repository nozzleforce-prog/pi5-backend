package com.ticket.backend.plc;

import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;
import com.intelligt.modbus.jlibmodbus.master.ModbusMasterFactory;
import com.intelligt.modbus.jlibmodbus.tcp.TcpParameters;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;

/**
 * Simple loop: enter GMT address and value, write to PLC, read back.
 * Args: [ip] [port] [unitId]
 */
public final class PlcWriteLoop {

    public static void main(String[] args) throws Exception {
        String ip = args.length > 0 ? args[0] : "169.254.179.226";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 502;
        int unitId = args.length > 2 ? Integer.parseInt(args[2]) : 1;

        TcpParameters tcp = new TcpParameters();
        tcp.setHost(InetAddress.getByName(ip));
        tcp.setPort(port);
        tcp.setKeepAlive(true);

        ModbusMaster master = ModbusMasterFactory.createModbusMasterTCP(tcp);
        master.connect();

        System.out.println("========================================");
        System.out.println("  PLC manual write loop");
        System.out.println("  Target : " + ip + ":" + port + "  unitId=" + unitId);
        System.out.println("  GMT    : 40001=modBilgisi  40002=durumBilgisi  40003=alarmDurumu");
        System.out.println("========================================");
        System.out.println("Enter address and value. Check GMT Suite after each write.");
        System.out.println("Leave value empty to read only. Type q to quit.");
        System.out.println();

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("Address : ");
            String addrLine = in.readLine();
            if (addrLine == null) {
                break;
            }
            addrLine = addrLine.trim();
            if (addrLine.isEmpty()) {
                continue;
            }
            if ("q".equalsIgnoreCase(addrLine) || "quit".equalsIgnoreCase(addrLine)) {
                break;
            }

            int address;
            try {
                address = Integer.parseInt(addrLine);
            } catch (NumberFormatException e) {
                System.out.println("  Invalid address. Use GMT number e.g. 40001");
                System.out.println();
                continue;
            }

            System.out.print("Value   : ");
            String valueLine = in.readLine();
            if (valueLine == null) {
                break;
            }
            valueLine = valueLine.trim();
            if ("q".equalsIgnoreCase(valueLine)) {
                break;
            }

            try {
                if (valueLine.isEmpty()) {
                    Integer value = readRegister(master, unitId, address);
                    if (value == null) {
                        System.out.println("  READ FAILED");
                    } else {
                        System.out.printf("  READ  %d = %d  (bits: %s)%n",
                                address, value, formatBits(value));
                    }
                } else {
                    int value = Integer.parseInt(valueLine);
                    Integer before = readRegister(master, unitId, address);
                    boolean ok = writeRegister(master, unitId, address, value);
                    Integer after = readRegister(master, unitId, address);
                    System.out.printf("  WRITE %d = %d -> %s%n", address, value, ok ? "OK" : "FAILED");
                    System.out.printf("  before=%s%n", formatNum(before));
                    System.out.printf("  after =%s%n", formatNum(after));
                    System.out.println("  >> Check GMT Suite now");
                }
            } catch (NumberFormatException e) {
                System.out.println("  Invalid value. Use integer e.g. 0, 1, 128");
            } catch (Exception e) {
                System.out.println("  ERROR: " + e.getMessage());
            }
            System.out.println();
        }

        master.disconnect();
        System.out.println("Done.");
    }

    private static Integer readRegister(ModbusMaster master, int unitId, int address) {
        try {
            int[] values = master.readHoldingRegisters(unitId, address - 40001, 1);
            return values != null && values.length > 0 ? values[0] : null;
        } catch (Exception e) {
            System.out.println("  READ ERROR: " + e.getMessage());
            return null;
        }
    }

    private static boolean writeRegister(ModbusMaster master, int unitId, int address, int value) {
        try {
            master.writeSingleRegister(unitId, address - 40001, value);
            return true;
        } catch (Exception e) {
            System.out.println("  WRITE ERROR: " + e.getMessage());
            return false;
        }
    }

    private static String formatNum(Integer value) {
        if (value == null) {
            return "null";
        }
        return value + " (bits: " + formatBits(value) + ")";
    }

    private static String formatBits(int value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 15; i >= 0; i--) {
            if ((value & (1 << i)) != 0) {
                if (!sb.isEmpty()) {
                    sb.append(',');
                }
                sb.append(i);
            }
        }
        return sb.isEmpty() ? "none" : sb.toString();
    }
}
