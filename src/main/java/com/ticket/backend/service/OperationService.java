package com.ticket.backend.service;

import com.ticket.backend.model.Operation;
import com.ticket.backend.repository.DeviceRepository;
import com.ticket.backend.repository.OperationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OperationService {

    private final OperationRepository operationRepository;
    private final DeviceRepository deviceRepository;

    public OperationService(OperationRepository operationRepository, DeviceRepository deviceRepository) {
        this.operationRepository = operationRepository;
        this.deviceRepository = deviceRepository;
    }

    public Operation addOperation(String name, int operationFee) {
        validateName(name);
        if (operationFee < 0) {
            throw new IllegalArgumentException("operationFee must be >= 0");
        }
        if (operationRepository.existsByNameIgnoreCase(name.trim())) {
            throw new IllegalArgumentException("Operation already exists: " + name);
        }

        Operation op = new Operation();
        op.setOperationCode(nextOperationCode());
        op.setName(name.trim());
        op.setOperationFee(operationFee);
        return operationRepository.save(op);
    }

    public Operation editOperation(String operationId, String name, Integer operationFee) {
        Operation op = getOperationOrThrow(operationId);

        if (name != null && !name.isBlank()) {
            String trimmed = name.trim();
            operationRepository.findByNameIgnoreCase(trimmed).ifPresent(existing -> {
                if (!existing.getId().equals(operationId)) {
                    throw new IllegalArgumentException("Operation name already in use: " + trimmed);
                }
            });
            op.setName(trimmed);
        }
        if (operationFee != null) {
            if (operationFee < 0) {
                throw new IllegalArgumentException("operationFee must be >= 0");
            }
            op.setOperationFee(operationFee);
        }
        return operationRepository.save(op);
    }

    public void deleteOperation(String operationId) {
        Operation op = getOperationOrThrow(operationId);
        boolean inUse = deviceRepository.findAll().stream()
                .anyMatch(d -> operationId.equals(d.getOperationId()));
        if (inUse) {
            throw new IllegalArgumentException("Operation is assigned to a device: " + op.getName());
        }
        operationRepository.delete(op);
    }

    public List<Operation> getAllOperations() {
        return operationRepository.findAllByOrderByNameAsc();
    }

    public Operation getOperation(String operationId) {
        return getOperationOrThrow(operationId);
    }

    public Operation getByOperationCode(int operationCode) {
        return operationRepository.findByOperationCode(operationCode)
                .orElseThrow(() -> new IllegalArgumentException("Operation not found for code: " + operationCode));
    }

    public Operation getByName(String name) {
        return operationRepository.findByNameIgnoreCase(name.trim())
                .orElseThrow(() -> new IllegalArgumentException("Operation not found: " + name));
    }

    private Operation getOperationOrThrow(String operationId) {
        return operationRepository.findById(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Operation not found: " + operationId));
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Operation name is required");
        }
    }

    private int nextOperationCode() {
        return operationRepository.findAll().stream()
                .mapToInt(Operation::getOperationCode)
                .max()
                .orElse(0) + 1;
    }
}
