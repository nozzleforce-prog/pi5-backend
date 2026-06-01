package com.ticket.backend.dto.response;

import com.ticket.backend.model.Ticket;
import com.ticket.backend.model.ValidationMode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Date;

@NoArgsConstructor
@Getter
public class TicketResponse {
    private String rfidCardId;
    private String name;
    private String number;
    private ValidationMode mode;
    private int balance;
    private Date expiration;
    private Date createdAt;

    public TicketResponse(
            String rfidCardId,
            String name,
            String number,
            ValidationMode mode,
            int balance,
            Date expiration,
            Date createdAt) {
        this.rfidCardId = rfidCardId;
        this.name = name;
        this.number = number;
        this.mode = mode;
        this.balance = balance;
        this.expiration = expiration;
        this.createdAt = createdAt;
    }

    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(
                ticket.getBarcode(),
                ticket.getName(),
                ticket.getNumber(),
                ticket.getMode(),
                ticket.getBalance(),
                ticket.getExpiresAt(),
                ticket.getCreatedAt()
        );
    }
}
