package com.ticket.backend.controller;

import com.ticket.backend.dto.request.AddDeviceRequest;
import com.ticket.backend.dto.request.AddOperationRequest;
import com.ticket.backend.dto.request.EditDeviceRequest;
import com.ticket.backend.dto.request.EditOperationRequest;
import com.ticket.backend.dto.response.DeviceResponse;
import com.ticket.backend.dto.response.OperationResponse;
import com.ticket.backend.model.Device;
import com.ticket.backend.model.Operation;
import com.ticket.backend.service.DeviceService;
import com.ticket.backend.service.OperationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final OperationService operationService;

    public DeviceController(DeviceService deviceService, OperationService operationService) {
        this.deviceService = deviceService;
        this.operationService = operationService;
    }

    @GetMapping("/scan")
    public ResponseEntity<Map<String, List<String>>> scanConnectedIps() {
        return ResponseEntity.ok(Map.of("ips", deviceService.getConnectedDeviceIps()));
    }

    @GetMapping("/operations")
    public ResponseEntity<List<OperationResponse>> getAllOperations() {
        List<OperationResponse> list = operationService.getAllOperations().stream()
                .map(OperationResponse::from)
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/operations/{operationId}")
    public ResponseEntity<OperationResponse> getOperation(@PathVariable String operationId) {
        return ResponseEntity.ok(OperationResponse.from(operationService.getOperation(operationId)));
    }

    @PostMapping("/operations")
    public ResponseEntity<OperationResponse> addOperation(@RequestBody AddOperationRequest request) {
        Operation op = operationService.addOperation(request.getName(), request.getOperationFee());
        return ResponseEntity.ok(OperationResponse.from(op));
    }

    @PutMapping("/operations/{operationId}")
    public ResponseEntity<OperationResponse> editOperation(
            @PathVariable String operationId,
            @RequestBody EditOperationRequest request) {
        Operation op = operationService.editOperation(
                operationId, request.getName(), request.getOperationFee());
        return ResponseEntity.ok(OperationResponse.from(op));
    }

    @DeleteMapping("/operations/{operationId}")
    public ResponseEntity<Void> deleteOperation(@PathVariable String operationId) {
        operationService.deleteOperation(operationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<DeviceResponse>> getAllDevices() {
        List<DeviceResponse> list = deviceService.getAllDevices().stream()
                .map(d -> DeviceResponse.from(d, deviceService.resolveOperation(d)))
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<DeviceResponse> getDevice(@PathVariable String deviceId) {
        Device device = deviceService.getDevice(deviceId);
        return ResponseEntity.ok(DeviceResponse.from(device, deviceService.resolveOperation(device)));
    }

    @PostMapping
    public ResponseEntity<DeviceResponse> addDevice(@RequestBody AddDeviceRequest request) {
        Device device = deviceService.addDevice(request.getDeviceIp(), request.getOperationName());
        return ResponseEntity.ok(DeviceResponse.from(device, deviceService.resolveOperation(device)));
    }

    @PutMapping("/{deviceId}")
    public ResponseEntity<DeviceResponse> editDevice(
            @PathVariable String deviceId,
            @RequestBody EditDeviceRequest request) {
        Device device = deviceService.editDevice(
                deviceId, request.getDeviceIp(), request.getOperationName(), request.getActive());
        return ResponseEntity.ok(DeviceResponse.from(device, deviceService.resolveOperation(device)));
    }

    @PostMapping("/{deviceId}/inactivate")
    public ResponseEntity<DeviceResponse> inactivateDevice(@PathVariable String deviceId) {
        Device device = deviceService.inactivateDevice(deviceId);
        return ResponseEntity.ok(DeviceResponse.from(device, deviceService.resolveOperation(device)));
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<Void> deleteDevice(@PathVariable String deviceId) {
        deviceService.deleteDevice(deviceId);
        return ResponseEntity.noContent().build();
    }
}
