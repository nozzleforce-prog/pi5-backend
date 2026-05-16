package com.ticket.backend.constants;

import com.ticket.backend.model.ValidationMode;

import java.util.HashMap;
import java.util.Map;

public class TicketConstants {
    public static Map<ValidationMode, Double> modeToPrice = new HashMap<>() {{
        put(ValidationMode.BASIC, 100.0);
        put(ValidationMode.FULL, 150.0);
        put(ValidationMode.EXTRA, 200.0);
        put(ValidationMode.INVALID, 0.0);
    }};
}
