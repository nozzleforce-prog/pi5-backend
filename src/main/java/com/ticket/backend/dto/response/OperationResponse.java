package com.ticket.backend.dto.response;

import com.ticket.backend.model.Operation;
import lombok.Getter;

@Getter
public class OperationResponse {
    private final String id;
    private final String name;
    private final int operationCode;
    private final int operationFee;
    private final int durationSeconds;

    public OperationResponse(String id, int operationCode, String name, int operationFee, int durationSeconds) {
        this.id = id;
        this.operationCode = operationCode;
        this.name = name;
        this.operationFee = operationFee;
        this.durationSeconds = durationSeconds;
    }

    public static OperationResponse from(Operation op) {
        return new OperationResponse(
                op.getId(),
                op.getOperationCode(),
                op.getName(),
                op.getOperationFee(),
                op.getDurationSeconds());
    }
}
