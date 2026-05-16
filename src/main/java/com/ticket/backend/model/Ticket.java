package com.ticket.backend.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "tickets")
@Getter
@Setter
public class Ticket {
    @Id
    private String id;
    private String barcode;
    private double price;
    private Date createdAt;
    private Date expiresAt;
    private TicketStatus status;
    private ValidationMode mode;
}


