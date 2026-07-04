package com.ticket.backend.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "operations")
@Getter
@Setter
public class Operation {
    @Id
    private String id;

    @Indexed(unique = true)
    private int operationCode;

    @Indexed(unique = true)
    private String name;

    private int operationFee;

    /** Makine calisma suresi (saniye, cozunurluk 1) — PLC time register degerine yazilir */
    private int durationSeconds;
}
