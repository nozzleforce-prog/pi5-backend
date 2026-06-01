package com.ticket.backend.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AddOperationRequest {
    private String name;
    private int operationFee;
}
