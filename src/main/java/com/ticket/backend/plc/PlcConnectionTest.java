package com.ticket.backend.plc;

import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;
import com.intelligt.modbus.jlibmodbus.master.ModbusMasterFactory;
import com.intelligt.modbus.jlibmodbus.tcp.TcpParameters;

import java.net.InetAddress;

/** CLI: PlcConnectionTest <ip> <port> <unitId> <register> */
public final class PlcConnectionTest {

    public static void main(String[] args) throws Exception {
        String ip = args.length > 0 ? args[0] : "169.254.179.226";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 502;
        int unitId = args.length > 2 ? Integer.parseInt(args[2]) : 1;
        int register = args.length > 3 ? Integer.parseInt(args[3]) : 40001;
        String mode = args.length > 4 ? args[4] : "read";
        int writeValue = args.length > 5 ? Integer.parseInt(args[5]) : 0;

        TcpParameters tcp = new TcpParameters();
        tcp.setHost(InetAddress.getByName(ip));
        tcp.setPort(port);
        tcp.setKeepAlive(true);

        ModbusMaster master = ModbusMasterFactory.createModbusMasterTCP(tcp);
        master.connect();

        if ("write".equalsIgnoreCase(mode)) {
            master.writeSingleRegister(unitId, register - 40001, writeValue);
            int[] after = master.readHoldingRegisters(unitId, register - 40001, 1);
            int value = after != null && after.length > 0 ? after[0] : -1;
            System.out.printf("OK: WROTE register=%d value=%d readback=%d%n", register, writeValue, value);
        } else {
            int[] values = master.readHoldingRegisters(unitId, register - 40001, 1);
            int value = values != null && values.length > 0 ? values[0] : -1;
            System.out.printf("OK: READ register=%d value=%d%n", register, value);
        }

        master.disconnect();
    }

    private PlcConnectionTest() {
    }
}
