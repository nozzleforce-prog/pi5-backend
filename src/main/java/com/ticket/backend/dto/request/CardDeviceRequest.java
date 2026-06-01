package com.ticket.backend.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CardDeviceRequest {
    private String rfidCardId;
    private String deviceId;
}
