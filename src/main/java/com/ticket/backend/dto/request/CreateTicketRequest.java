package com.ticket.backend.dto.request;

import com.ticket.backend.model.ValidationMode;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Getter
public class CreateTicketRequest {
    private ValidationMode mode;
}
