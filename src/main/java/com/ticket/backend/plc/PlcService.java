package com.ticket.backend.plc;

import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;
import com.intelligt.modbus.jlibmodbus.master.ModbusMasterFactory;
import com.intelligt.modbus.jlibmodbus.tcp.TcpParameters;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;

@Service
public class PlcService {

    private static final Logger log = LoggerFactory.getLogger(PlcService.class);

    private ModbusMaster master;

    @Value("${plc.enabled:true}")
    private boolean enabled;

    @Value("${plc.ip}")
    private String ip;

    @Value("${plc.port}")
    private int port;

    @Value("${plc.unitId:1}")
    private int unitId;

    @PostConstruct
    public void connect() {
        if (!enabled) {
            log.info("PLC devre disi (plc.enabled=false)");
            return;
        }
        reconnect();
    }

    private synchronized void reconnect() {
        if (!enabled) {
            return;
        }
        try {
            if (master != null && master.isConnected()) {
                master.disconnect();
            }
            TcpParameters tcpParameters = new TcpParameters();
            tcpParameters.setHost(InetAddress.getByName(ip));
            tcpParameters.setPort(port);
            tcpParameters.setKeepAlive(true);

            master = ModbusMasterFactory.createModbusMasterTCP(tcpParameters);
            master.connect();
            log.info("PLC baglandi: {}:{} unitId={}", ip, port, unitId);
        } catch (Exception e) {
            log.error("PLC baglantisi basarisiz ({}:{}): {}", ip, port, e.getMessage());
            master = null;
        }
    }

    private synchronized void ensureConnected() {
        if (!enabled) {
            return;
        }
        if (master == null || !master.isConnected()) {
            reconnect();
        }
    }

    public synchronized Integer readRegister(int address) {
        if (!enabled) {
            return null;
        }
        ensureConnected();
        if (master == null || !master.isConnected()) {
            return null;
        }
        try {
            int[] registers = master.readHoldingRegisters(unitId, address - 40001, 1);
            return (registers != null && registers.length > 0) ? registers[0] : null;
        } catch (Exception e) {
            log.warn("PLC okuma hatasi register {}: {}", address, e.getMessage());
            reconnect();
            return null;
        }
    }

    public synchronized boolean writeRegister(int address, int value) {
        if (!enabled) {
            return false;
        }
        ensureConnected();
        if (master == null || !master.isConnected()) {
            return false;
        }
        try {
            master.writeSingleRegister(unitId, address - 40001, value);
            return true;
        } catch (Exception e) {
            log.warn("PLC yazma hatasi register {} value {}: {}", address, value, e.getMessage());
            reconnect();
            return false;
        }
    }
}
