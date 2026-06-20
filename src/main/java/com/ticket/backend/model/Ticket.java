package com.ticket.backend.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "tickets")
@Getter
@Setter
public class Ticket {
    @Id
    private String id;

    /** RFID kart kimligi (eski barcode alani) */
    @Indexed(unique = true)
    private String barcode;

    private String name;
    private String number;
    private int balance;
    private Date createdAt;
    private TicketStatus status;
}
