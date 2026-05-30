package com.vanguard.ingest.controller;

import com.vanguard.ingest.dto.IngestResponse;
import com.vanguard.ingest.dto.TransactionRequest;
import com.vanguard.ingest.ratelimit.RateLimited;
import com.vanguard.ingest.service.TransactionIngestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionIngestService ingestService;

    public TransactionController(TransactionIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping
    @RateLimited(capacity = 5, refillPerMinute = 5)
    public ResponseEntity<IngestResponse> ingestTransaction(
            @Valid @RequestBody TransactionRequest request) {
        IngestResponse response = ingestService.ingest(request);
        return switch (response.status()) {
            case "ACCEPTED" -> ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            case "ALREADY_PROCESSED" -> ResponseEntity.ok(response);
            default -> ResponseEntity.badRequest().body(response);
        };
    }
}
