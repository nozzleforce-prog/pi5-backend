package com.ticket.backend.dto.response;

import com.ticket.backend.model.ValidationMode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Date;

@NoArgsConstructor
@Getter
public class TicketResponse {
    private String ticketCode;
    private ValidationMode mode;
    private double price;
    private Date expiration;
    private Date createdAt;

    public TicketResponse(String ticketCode, ValidationMode mode, double price, Date expiration, Date createdAt) {
        this.ticketCode = ticketCode;
        this.mode = mode;
        this.price = price;
        this.expiration = expiration;
        this.createdAt = createdAt;
    }
}


