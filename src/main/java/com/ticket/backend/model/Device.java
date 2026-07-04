package com.ticket.backend.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "devices")
@Getter
@Setter
public class Device {
    @Id
    private String id;

    @Indexed(unique = true)
    private String deviceId;

    /** RP2350 / CH9120 ag adresi — birden fazla cihaz ayni okuyucuyu paylasabilir */
    private String deviceIp;

    private boolean active = true;

    /** {@link Operation} belgesinin id alani */
    private String operationId;

    private int plcBit;

    /** p4-nano | rp2350 — TCP protokol ailesi */
    private String readerType = "p4-nano";
}
