package com.ticket.backend.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LoadMoneyRequest {
    private String rfidCardId;
    private int amount;
}
