package com.ticket.backend.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class EditDeviceRequest {
    private String deviceIp;
    private String operationName;
    private Boolean active;
}
