package com.ticket.backend.constants;

import com.ticket.backend.model.DeviceType;
import com.ticket.backend.model.ValidationMode;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TicketConstants {
    public static final Map<ValidationMode, Double> modeToPrice = new HashMap<>() {{
        put(ValidationMode.BASIC, 100.0);
        put(ValidationMode.FULL, 150.0);
        put(ValidationMode.EXTRA, 200.0);
        put(ValidationMode.INVALID, 0.0);
    }};

    /** Hangi bilet modunun hangi cihaz tipinde kullanilabilecegi */
    public static final Map<DeviceType, Set<ValidationMode>> allowedModesByDeviceType = new HashMap<>() {{
        put(DeviceType.WATER, EnumSet.of(ValidationMode.BASIC, ValidationMode.FULL, ValidationMode.EXTRA));
        put(DeviceType.FOAM, EnumSet.of(ValidationMode.FULL, ValidationMode.EXTRA));
        put(DeviceType.WASH_MACHINE, EnumSet.of(ValidationMode.FULL, ValidationMode.EXTRA));
        put(DeviceType.VACUUM, EnumSet.of(ValidationMode.BASIC, ValidationMode.FULL, ValidationMode.EXTRA));
        put(DeviceType.DRYER, EnumSet.of(ValidationMode.FULL, ValidationMode.EXTRA));
        put(DeviceType.BRUSH, EnumSet.of(ValidationMode.FULL, ValidationMode.EXTRA));
        put(DeviceType.OTHER, EnumSet.of(ValidationMode.BASIC, ValidationMode.FULL, ValidationMode.EXTRA));
    }};

    public static boolean isModeAllowedOnDevice(ValidationMode mode, DeviceType deviceType) {
        if (mode == null || deviceType == null || mode == ValidationMode.INVALID) {
            return false;
        }
        return allowedModesByDeviceType
                .getOrDefault(deviceType, EnumSet.noneOf(ValidationMode.class))
                .contains(mode);
    }
}
