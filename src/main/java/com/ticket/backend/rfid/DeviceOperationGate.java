package com.ticket.backend.rfid;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cihaz bazli calisma kilidi: PLC state=1 olduktan sonra state=0 olana kadar
 * ayni deviceId icin yeni okuma kuyruga alinmaz.
 */
@Component
public class DeviceOperationGate {

    private final Set<String> runningDeviceIds = ConcurrentHashMap.newKeySet();

    public boolean isDeviceRunning(String deviceId) {
        if (deviceId == null || deviceId.isBlank() || "unknown".equals(deviceId)) {
            return false;
        }
        return runningDeviceIds.contains(deviceId.trim());
    }

    public void markRunning(String deviceId) {
        if (deviceId != null && !deviceId.isBlank() && !"unknown".equals(deviceId)) {
            runningDeviceIds.add(deviceId.trim());
        }
    }

    public void markIdle(String deviceId) {
        if (deviceId != null && !deviceId.isBlank()) {
            runningDeviceIds.remove(deviceId.trim());
        }
    }
}
