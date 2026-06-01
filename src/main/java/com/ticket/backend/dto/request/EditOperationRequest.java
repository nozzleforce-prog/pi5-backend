package com.ticket.backend.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class EditOperationRequest {
    private String name;
    private Integer operationFee;
}
