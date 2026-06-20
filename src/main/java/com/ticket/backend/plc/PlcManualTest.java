package com.ticket.backend.plc;

import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;
import com.intelligt.modbus.jlibmodbus.master.ModbusMasterFactory;
import com.intelligt.modbus.jlibmodbus.tcp.TcpParameters;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.Scanner;

/**
 * Interactive Modbus holding-register test (GMT 40001-based addresses).
 * <p>
 * Usage:
 * <pre>
 *   PlcManualTest [ip] [port] [unitId]
 *   PlcManualTest read  40001
 *   PlcManualTest write 40001 1
 *   PlcManualTest setbit 40001 0
 * </pre>
 */
public final class PlcManualTest {

    private final ModbusMaster master;
    private final int unitId;
    private final String target;

    private PlcManualTest(ModbusMaster master, int unitId, String target) {
        this.master = master;
        this.unitId = unitId;
        this.target = target;
    }

    public static void main(String[] args) throws Exception {
        String ip = "169.254.179.226";
        int port = 502;
        int unitId = 1;
        int argIdx = 0;

        if (args.length > argIdx && !isCommand(args[argIdx])) {
            ip = args[argIdx++];
        }
        if (args.length > argIdx && !isCommand(args[argIdx])) {
            port = Integer.parseInt(args[argIdx++]);
        }
        if (args.length > argIdx && !isCommand(args[argIdx])) {
            unitId = Integer.parseInt(args[argIdx++]);
        }

        TcpParameters tcp = new TcpParameters();
        tcp.setHost(InetAddress.getByName(ip));
        tcp.setPort(port);
        tcp.setKeepAlive(true);

        ModbusMaster master = ModbusMasterFactory.createModbusMasterTCP(tcp);
        master.connect();

        PlcManualTest test = new PlcManualTest(master, unitId, ip + ":" + port + " unit=" + unitId);
        System.out.println("Connected: " + test.target);
        System.out.println("Register addresses use GMT style (40001 = modBilgisi, 40002 = durumBilgisi).");
        System.out.println();

        if (argIdx < args.length) {
            test.runOnce(args, argIdx);
        } else {
            test.runInteractive();
        }

        master.disconnect();
    }

    private static boolean isCommand(String s) {
        return switch (s.toLowerCase()) {
            case "read", "r", "write", "w", "setbit", "clearbit", "bits", "help", "quit", "q" -> true;
            default -> false;
        };
    }

    private void runOnce(String[] args, int start) throws Exception {
        execute(String.join(" ", java.util.Arrays.copyOfRange(args, start, args.length)));
    }

    private void runInteractive() throws Exception {
        printHelp();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("plc> ");
            String line = reader.readLine();
            if (line == null || line.isBlank()) {
                continue;
            }
            String cmd = line.trim();
            if ("quit".equalsIgnoreCase(cmd) || "q".equalsIgnoreCase(cmd)) {
                break;
            }
            if ("help".equalsIgnoreCase(cmd) || "?".equalsIgnoreCase(cmd)) {
                printHelp();
                continue;
            }
            execute(cmd);
        }
    }

    private void execute(String line) throws Exception {
        Scanner sc = new Scanner(line);
        if (!sc.hasNext()) {
            return;
        }
        String op = sc.next().toLowerCase();
        try {
            switch (op) {
                case "read", "r" -> {
                    int address = sc.nextInt();
                    Integer value = readRegister(address);
                    if (value == null) {
                        System.out.println("READ FAILED register=" + address);
                    } else {
                        System.out.printf("READ  register=%d value=%d (0x%04X) bits=%s%n",
                                address, value, value & 0xFFFF, formatBits(value));
                    }
                }
                case "write", "w" -> {
                    int address = sc.nextInt();
                    int value = sc.nextInt();
                    Integer before = readRegister(address);
                    boolean ok = writeRegister(address, value);
                    Integer after = readRegister(address);
                    System.out.printf("WRITE register=%d value=%d -> %s%n", address, value, ok ? "OK" : "FAILED");
                    System.out.printf("  before=%s after=%s%n", formatValue(before), formatValue(after));
                }
                case "setbit" -> {
                    int address = sc.nextInt();
                    int bit = sc.nextInt();
                    Integer before = readRegister(address);
                    if (before == null) {
                        System.out.println("SETBIT FAILED (read error) register=" + address);
                        break;
                    }
                    int value = before | (1 << bit);
                    boolean ok = writeRegister(address, value);
                    Integer after = readRegister(address);
                    System.out.printf("SETBIT register=%d bit=%d mask=%d -> %s%n",
                            address, bit, 1 << bit, ok ? "OK" : "FAILED");
                    System.out.printf("  before=%s after=%s%n", formatValue(before), formatValue(after));
                }
                case "clearbit" -> {
                    int address = sc.nextInt();
                    int bit = sc.nextInt();
                    Integer before = readRegister(address);
                    if (before == null) {
                        System.out.println("CLEARBIT FAILED (read error) register=" + address);
                        break;
                    }
                    int value = before & ~(1 << bit);
                    boolean ok = writeRegister(address, value);
                    Integer after = readRegister(address);
                    System.out.printf("CLEARBIT register=%d bit=%d -> %s%n", address, bit, ok ? "OK" : "FAILED");
                    System.out.printf("  before=%s after=%s%n", formatValue(before), formatValue(after));
                }
                case "bits" -> {
                    int address = sc.nextInt();
                    Integer value = readRegister(address);
                    if (value == null) {
                        System.out.println("BITS FAILED register=" + address);
                    } else {
                        System.out.printf("BITS register=%d value=%d -> %s%n", address, value, formatBits(value));
                    }
                }
                default -> System.out.println("Unknown command. Type 'help'.");
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
        }
    }

    private Integer readRegister(int address) {
        try {
            int[] values = master.readHoldingRegisters(unitId, address - 40001, 1);
            return values != null && values.length > 0 ? values[0] : null;
        } catch (Exception e) {
            System.out.println("READ ERROR register=" + address + ": " + e.getMessage());
            return null;
        }
    }

    private boolean writeRegister(int address, int value) {
        try {
            master.writeSingleRegister(unitId, address - 40001, value);
            return true;
        } catch (Exception e) {
            System.out.println("WRITE ERROR register=" + address + " value=" + value + ": " + e.getMessage());
            return false;
        }
    }

    private static String formatValue(Integer value) {
        if (value == null) {
            return "null";
        }
        return value + " (0x" + Integer.toHexString(value & 0xFFFF).toUpperCase() + ", bits=" + formatBits(value) + ")";
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

    private static void printHelp() {
        System.out.println("Commands:");
        System.out.println("  read <address>              Read holding register (e.g. read 40001)");
        System.out.println("  write <address> <value>     Write full register value (e.g. write 40001 1)");
        System.out.println("  setbit <address> <bit>      Set one bit read-modify-write (e.g. setbit 40001 0)");
        System.out.println("  clearbit <address> <bit>    Clear one bit (e.g. clearbit 40001 0)");
        System.out.println("  bits <address>              Show which bits are set");
        System.out.println("  help                        Show this help");
        System.out.println("  quit                        Exit");
        System.out.println();
    }
}
