package com.ticket.backend.dto.response;

import com.ticket.backend.model.CardOperationLog;
import com.ticket.backend.model.CardOperationType;
import com.ticket.backend.model.TicketStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Date;

@NoArgsConstructor
@Getter
public class CardOperationLogResponse {
    private String id;
    private CardOperationType type;
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

    public CardOperationLogResponse(
            String id,
            CardOperationType type,
            String rfidCardId,
            String deviceId,
            Date performedAt,
            Integer amount,
            Integer balanceBefore,
            Integer balanceAfter,
            String name,
            String number,
            TicketStatus status,
            String details,
            String performedBy) {
        this.id = id;
        this.type = type;
        this.rfidCardId = rfidCardId;
        this.deviceId = deviceId;
        this.performedAt = performedAt;
        this.amount = amount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.name = name;
        this.number = number;
        this.status = status;
        this.details = details;
        this.performedBy = performedBy;
    }

    public static CardOperationLogResponse from(CardOperationLog log) {
        return new CardOperationLogResponse(
                log.getId(),
                log.getType(),
                log.getRfidCardId(),
                log.getDeviceId(),
                log.getPerformedAt(),
                log.getAmount(),
                log.getBalanceBefore(),
                log.getBalanceAfter(),
                log.getName(),
                log.getNumber(),
                log.getStatus(),
                log.getDetails(),
                log.getPerformedBy());
    }
}
