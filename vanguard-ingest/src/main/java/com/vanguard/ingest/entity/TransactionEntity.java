package com.vanguard.ingest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions")
public class TransactionEntity {

    @Id
    @Column(length = 64)
    private String transactionId;

    @Column(nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(precision = 18, scale = 4)
    private BigDecimal oldBalanceOrig;

    @Column(precision = 18, scale = 4)
    private BigDecimal newBalanceOrig;

    @Column(precision = 18, scale = 4)
    private BigDecimal oldBalanceDest;

    @Column(precision = 18, scale = 4)
    private BigDecimal newBalanceDest;

    @Column(nullable = false, length = 16)
    private String transactionType;

    @Column(length = 64)
    private String merchantId;

    @Column(length = 32)
    private String merchantCategory;

    private Double latitude;
    private Double longitude;

    @Column(length = 64)
    private String deviceId;

    @Column(length = 45)
    private String ipAddress;

    @Column(length = 3)
    private String currency;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private Instant ingestedAt;

    @Column(length = 16)
    private String status;

    private Float fraudScore;

    private Boolean isHighRisk;

    private Long scoringLatencyMs;

    private Instant scoredAt;

    public TransactionEntity() {}

    public TransactionEntity(String transactionId, String userId, BigDecimal amount,
                             BigDecimal oldBalanceOrig, BigDecimal newBalanceOrig,
                             BigDecimal oldBalanceDest, BigDecimal newBalanceDest,
                             String transactionType,
                             String merchantId, String merchantCategory,
                             Double latitude, Double longitude,
                             String deviceId, String ipAddress,
                             String currency, Instant timestamp) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.amount = amount;
        this.oldBalanceOrig = oldBalanceOrig;
        this.newBalanceOrig = newBalanceOrig;
        this.oldBalanceDest = oldBalanceDest;
        this.newBalanceDest = newBalanceDest;
        this.transactionType = transactionType;
        this.merchantId = merchantId;
        this.merchantCategory = merchantCategory;
        this.latitude = latitude;
        this.longitude = longitude;
        this.deviceId = deviceId;
        this.ipAddress = ipAddress;
        this.currency = currency;
        this.timestamp = timestamp;
        this.ingestedAt = Instant.now();
        this.status = "PENDING";
    }

    public String getTransactionId() { return transactionId; }
    public String getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getOldBalanceOrig() { return oldBalanceOrig; }
    public BigDecimal getNewBalanceOrig() { return newBalanceOrig; }
    public BigDecimal getOldBalanceDest() { return oldBalanceDest; }
    public BigDecimal getNewBalanceDest() { return newBalanceDest; }
    public String getTransactionType() { return transactionType; }
    public String getMerchantId() { return merchantId; }
    public String getMerchantCategory() { return merchantCategory; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getDeviceId() { return deviceId; }
    public String getIpAddress() { return ipAddress; }
    public String getCurrency() { return currency; }
    public Instant getTimestamp() { return timestamp; }
    public Instant getIngestedAt() { return ingestedAt; }
    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }
}
