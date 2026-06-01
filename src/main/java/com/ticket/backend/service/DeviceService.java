package com.ticket.backend.service;

import com.ticket.backend.model.Device;
import com.ticket.backend.model.Operation;
import com.ticket.backend.repository.DeviceRepository;
import com.ticket.backend.rfid.RfidDeviceNetworkScanner;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final OperationService operationService;
    private final RfidDeviceNetworkScanner networkScanner;

    public DeviceService(
            DeviceRepository deviceRepository,
            OperationService operationService,
            RfidDeviceNetworkScanner networkScanner) {
        this.deviceRepository = deviceRepository;
        this.operationService = operationService;
        this.networkScanner = networkScanner;
    }

    public List<String> getConnectedDeviceIps() {
        return networkScanner.scanConnectedDeviceIps();
    }

    public Device addDevice(String deviceIp, String operationName) {
        if (deviceIp == null || deviceIp.isBlank()) {
            throw new IllegalArgumentException("deviceIp is required");
        }
        String ip = deviceIp.trim();
        if (deviceRepository.existsByDeviceIp(ip)) {
            throw new IllegalArgumentException("Device already registered for IP: " + ip);
        }

        Operation operation = operationService.getByName(operationName);

        Device device = new Device();
        device.setDeviceIp(ip);
        device.setOperationId(operation.getId());
        device.setDeviceId(nextDeviceId());
        device.setPlcBit(nextPlcBit());
        device.setActive(true);

        return deviceRepository.save(device);
    }

    public Device editDevice(String deviceId, String deviceIp, String operationName, Boolean active) {
        Device device = getByDeviceIdOrThrow(deviceId);

        if (deviceIp != null && !deviceIp.isBlank()) {
            String ip = deviceIp.trim();
            deviceRepository.findByDeviceIp(ip).ifPresent(other -> {
                if (!other.getId().equals(device.getId())) {
                    throw new IllegalArgumentException("IP already used: " + ip);
                }
            });
            device.setDeviceIp(ip);
        }
        if (operationName != null && !operationName.isBlank()) {
            Operation operation = operationService.getByName(operationName);
            device.setOperationId(operation.getId());
        }
        if (active != null) {
            device.setActive(active);
        }
        return deviceRepository.save(device);
    }

    public void deleteDevice(String deviceId) {
        Device device = getByDeviceIdOrThrow(deviceId);
        deviceRepository.delete(device);
    }

    public Device inactivateDevice(String deviceId) {
        Device device = getByDeviceIdOrThrow(deviceId);
        device.setActive(false);
        return deviceRepository.save(device);
    }

    public List<Device> getAllDevices() {
        return deviceRepository.findAllByOrderByDeviceIdAsc();
    }

    public Device getDevice(String deviceId) {
        return getByDeviceIdOrThrow(deviceId);
    }

    public Optional<Device> findActiveByDeviceId(String deviceId) {
        return deviceRepository.findByDeviceId(deviceId)
                .filter(Device::isActive);
    }

    public Operation resolveOperation(Device device) {
        if (device == null || device.getOperationId() == null) {
            throw new IllegalArgumentException("Device has no operation assigned");
        }
        return operationService.getOperation(device.getOperationId());
    }

    public int operationFeeFor(Device device) {
        return resolveOperation(device).getOperationFee();
    }

    private String nextDeviceId() {
        int max = deviceRepository.findAll().stream()
                .map(Device::getDeviceId)
                .filter(id -> id != null && id.matches("\\d+"))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0);
        return String.valueOf(max + 1);
    }

    private int nextPlcBit() {
        return deviceRepository.findAll().stream()
                .mapToInt(Device::getPlcBit)
                .max()
                .orElse(0) + 1;
    }

    private Device getByDeviceIdOrThrow(String deviceId) {
        return deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));
    }
}
