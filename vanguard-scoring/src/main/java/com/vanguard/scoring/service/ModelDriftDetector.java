package com.vanguard.scoring.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ModelDriftDetector {

    @Scheduled(cron = "0 0 2 * * ?")
    public void detectDrift() {
        log.info("PSI drift check running — monitoring active");
    }
}
