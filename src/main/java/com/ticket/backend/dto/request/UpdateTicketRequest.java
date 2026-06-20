package com.ticket.backend.dto.request;

import com.ticket.backend.model.TicketStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateTicketRequest {
    private String name;
    private String number;
    private Integer balance;
    private TicketStatus status;
}
