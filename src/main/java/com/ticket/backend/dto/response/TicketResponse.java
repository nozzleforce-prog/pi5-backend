package com.ticket.backend.dto.response;

import com.ticket.backend.model.Ticket;
import com.ticket.backend.model.TicketStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Date;

@NoArgsConstructor
@Getter
public class TicketResponse {
    private String rfidCardId;
    private String name;
    private String number;
    private TicketStatus status;
    private int balance;
    private Date createdAt;

    public TicketResponse(
            String rfidCardId,
            String name,
            String number,
            TicketStatus status,
            int balance,
            Date createdAt) {
        this.rfidCardId = rfidCardId;
        this.name = name;
        this.number = number;
        this.status = status;
        this.balance = balance;
        this.createdAt = createdAt;
    }

    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(
                ticket.getBarcode(),
                ticket.getName(),
                ticket.getNumber(),
                ticket.getStatus(),
                ticket.getBalance(),
                ticket.getCreatedAt()
        );
    }
}
