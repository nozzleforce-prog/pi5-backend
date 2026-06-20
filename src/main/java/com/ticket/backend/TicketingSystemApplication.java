package com.ticket.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.boot.context.properties.EnableConfigurationProperties(
        com.ticket.backend.config.SiteConfigurationProperties.class)
public class TicketingSystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(TicketingSystemApplication.class, args);
    }
}
