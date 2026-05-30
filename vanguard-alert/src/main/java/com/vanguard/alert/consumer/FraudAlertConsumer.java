package com.vanguard.alert.consumer;

import com.vanguard.common.FraudAlert;
import com.vanguard.common.KafkaTopics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class FraudAlertConsumer {

    private static final Logger log = LoggerFactory.getLogger(FraudAlertConsumer.class);

    private final SimpMessagingTemplate messagingTemplate;

    public FraudAlertConsumer(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @KafkaListener(topics = KafkaTopics.TXN_ALERTS, groupId = "vanguard-alert",
        containerFactory = "fraudAlertKafkaListenerContainerFactory")
    public void onFraudAlert(ConsumerRecord<String, FraudAlert> record) {
        FraudAlert alert = record.value();
        log.warn("Fraud alert: transaction={}, user={}, score={}",
            alert.transactionId(), alert.userId(), alert.fraudScore());

        messagingTemplate.convertAndSend("/topic/alerts", alert);
    }
}
