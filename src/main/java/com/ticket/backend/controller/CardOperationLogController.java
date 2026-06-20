package com.ticket.backend.controller;

import com.ticket.backend.dto.response.CardOperationLogResponse;
import com.ticket.backend.service.CardOperationLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/admin/operation-logs")
public class CardOperationLogController {

    private final CardOperationLogService cardOperationLogService;

    public CardOperationLogController(CardOperationLogService cardOperationLogService) {
        this.cardOperationLogService = cardOperationLogService;
    }

    @GetMapping
    public ResponseEntity<List<CardOperationLogResponse>> getLogs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date to) {
        return ResponseEntity.ok(cardOperationLogService.getLogs(from, to));
    }
}
