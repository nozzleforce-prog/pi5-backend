package com.ticket.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "site")
public class SiteConfigurationProperties {

    private List<OperationDef> operations = new ArrayList<>();
    private List<DeviceDef> devices = new ArrayList<>();

    public List<OperationDef> getOperations() {
        return operations;
    }

    public void setOperations(List<OperationDef> operations) {
        this.operations = operations != null ? operations : new ArrayList<>();
    }

    public List<DeviceDef> getDevices() {
        return devices;
    }

    public void setDevices(List<DeviceDef> devices) {
        this.devices = devices != null ? devices : new ArrayList<>();
    }

    public static class OperationDef {
        private int code;
        private String name;
        private int fee;
        private int durationSeconds;

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getFee() {
            return fee;
        }

        public void setFee(int fee) {
            this.fee = fee;
        }

        public int getDurationSeconds() {
            return durationSeconds;
        }

        public void setDurationSeconds(int durationSeconds) {
            this.durationSeconds = durationSeconds;
        }
    }

    public static class DeviceDef {
        private String deviceId;
        private String readerIp;
        private int plcBit;
        private int operationCode;
        private boolean active = true;
        private String readerType = "p4-nano";

        public String getReaderType() {
            return readerType;
        }

        public void setReaderType(String readerType) {
            this.readerType = readerType;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        public String getReaderIp() {
            return readerIp;
        }

        public void setReaderIp(String readerIp) {
            this.readerIp = readerIp;
        }

        public int getPlcBit() {
            return plcBit;
        }

        public void setPlcBit(int plcBit) {
            this.plcBit = plcBit;
        }

        public int getOperationCode() {
            return operationCode;
        }

        public void setOperationCode(int operationCode) {
            this.operationCode = operationCode;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }
}
