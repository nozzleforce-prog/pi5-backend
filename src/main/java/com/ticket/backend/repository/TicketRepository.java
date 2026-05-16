package com.ticket.backend.repository;

import com.ticket.backend.model.Ticket;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends MongoRepository<Ticket, String> {
    Optional<Ticket> findByBarcode(String barcode);

    List<Ticket> findAllByOrderByCreatedAtDesc();
}

