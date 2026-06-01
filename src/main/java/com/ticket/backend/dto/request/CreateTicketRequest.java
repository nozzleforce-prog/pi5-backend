package com.ticket.backend.dto.request;

import com.ticket.backend.model.ValidationMode;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateTicketRequest {
    /** RFID kart ID (barcode) */
    private String rfidCardId;
    /** Ilk yukleme tutari */
    private int loadAmount;
    private String name;
    private String number;
    private ValidationMode mode;
}
