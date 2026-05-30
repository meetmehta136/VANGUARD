package com.vanguard.scoring.controller;

import com.vanguard.scoring.service.FraudScoringService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class ModelReloadEndpoint {

    private final FraudScoringService scoringService;

    public ModelReloadEndpoint(FraudScoringService scoringService) {
        this.scoringService = scoringService;
    }

    @PostMapping("/models/reload")
    public ResponseEntity<Map<String, String>> reloadModels() {
        scoringService.reloadModel();
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Models reloaded"));
    }
}
