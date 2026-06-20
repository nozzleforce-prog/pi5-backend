package com.ticket.backend.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "card_operation_logs")
@Getter
@Setter
public class CardOperationLog {
    @Id
    private String id;

    private CardOperationType type;

    @Indexed
    private String rfidCardId;

    private String deviceId;
    private Date performedAt;
    private Integer amount;
    private Integer balanceBefore;
    private Integer balanceAfter;
    private String name;
    private String number;
    private TicketStatus status;
    private String details;
    private String performedBy;
}
