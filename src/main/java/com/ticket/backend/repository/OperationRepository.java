package com.ticket.backend.repository;

import com.ticket.backend.model.Operation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface OperationRepository extends MongoRepository<Operation, String> {
    Optional<Operation> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
    List<Operation> findAllByOrderByNameAsc();
}
