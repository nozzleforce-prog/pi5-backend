package com.ticket.backend.repository;

import com.ticket.backend.model.CardOperationLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;

public interface CardOperationLogRepository extends MongoRepository<CardOperationLog, String> {
    List<CardOperationLog> findAllByOrderByPerformedAtDesc();

    List<CardOperationLog> findByPerformedAtBetweenOrderByPerformedAtDesc(Date from, Date to);

    List<CardOperationLog> findByPerformedAtGreaterThanEqualOrderByPerformedAtDesc(Date from);

    List<CardOperationLog> findByPerformedAtLessThanEqualOrderByPerformedAtDesc(Date to);
}
