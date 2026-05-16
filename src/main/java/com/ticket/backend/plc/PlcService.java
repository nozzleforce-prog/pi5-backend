package com.ticket.backend.plc;

import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;
import com.intelligt.modbus.jlibmodbus.master.ModbusMasterFactory;
import com.intelligt.modbus.jlibmodbus.tcp.TcpParameters;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.InetAddress;

@Service
public class PlcService {
    private ModbusMaster master;

    @Value("${plc.ip}") private String ip;
    @Value("${plc.port}") private int port;
    @Value("${plc.unitId:1}") private int unitId; // Default to 1 if not in properties

    @PostConstruct
    public void connect() {
        try {
            TcpParameters tcpParameters = new TcpParameters();
            tcpParameters.setHost(InetAddress.getByName(ip));
            tcpParameters.setPort(port);
            tcpParameters.setKeepAlive(true); // Robustness for Pi 5 long-running tasks

            master = ModbusMasterFactory.createModbusMasterTCP(tcpParameters);
            master.connect();
        } catch (Exception e) {
            System.err.println("❌ PLC Connection Failed: " + e.getMessage());
        }
    }

    /**
     * Corrected: Added unitId as the first argument
     */
    public synchronized Integer readRegister(int address) {
        if (master == null || !master.isConnected()) connect();
        try {
            // jlibmodbus signature: readHoldingRegisters(slaveId, offset, quantity)
            int[] registers = master.readHoldingRegisters(unitId, address - 40001, 1);
            return (registers != null && registers.length > 0) ? registers[0] : null;
        } catch (Exception e) {
            System.err.println("❌ Read Error at " + address + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Corrected: Added unitId as the first argument
     */
    public synchronized boolean writeRegister(int address, int value) {
        if (master == null || !master.isConnected()) connect();
        try {
            // jlibmodbus signature: writeSingleRegister(slaveId, offset, value)
            master.writeSingleRegister(unitId, address - 40001, value);
            return true;
        } catch (Exception e) {
            System.err.println("❌ Write Error at " + address + ": " + e.getMessage());
            return false;
        }
    }
}