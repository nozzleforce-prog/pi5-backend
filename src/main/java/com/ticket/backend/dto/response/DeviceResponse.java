package com.ticket.backend.dto.response;

import com.ticket.backend.model.Device;
import com.ticket.backend.model.Operation;
import lombok.Getter;

@Getter
public class DeviceResponse {
    private final String id;
    private final String deviceId;
    private final String deviceIp;
    private final boolean active;
    private final int plcBit;
    private final OperationResponse operation;

    public DeviceResponse(
            String id,
            String deviceId,
            String deviceIp,
            boolean active,
            int plcBit,
            OperationResponse operation) {
        this.id = id;
        this.deviceId = deviceId;
        this.deviceIp = deviceIp;
        this.active = active;
        this.plcBit = plcBit;
        this.operation = operation;
    }

    public static DeviceResponse from(Device device, Operation operation) {
        return new DeviceResponse(
                device.getId(),
                device.getDeviceId(),
                device.getDeviceIp(),
                device.isActive(),
                device.getPlcBit(),
                operation != null ? OperationResponse.from(operation) : null
        );
    }
}
