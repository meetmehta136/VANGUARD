package com.vanguard.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VelocityAggregate implements Serializable {

    private int txnCount;
    private double sumAmount;
    private Set<String> uniqueMerchants;

    public VelocityAggregate() {
        this.txnCount = 0;
        this.sumAmount = 0.0;
        this.uniqueMerchants = new HashSet<>();
    }

    public VelocityAggregate add(Transaction txn) {
        this.txnCount++;
        this.sumAmount += txn.amount().doubleValue();
        if (txn.merchantId() != null) {
            this.uniqueMerchants.add(txn.merchantId());
        }
        return this;
    }

    public int getTxnCount() {
        return txnCount;
    }

    public double getSumAmount() {
        return sumAmount;
    }

    public Set<String> getUniqueMerchants() {
        return Collections.unmodifiableSet(uniqueMerchants);
    }

    @JsonIgnore
    public int getUniqueMerchantsCount() {
        return uniqueMerchants.size();
    }

    public void setTxnCount(int txnCount) {
        this.txnCount = txnCount;
    }

    public void setSumAmount(double sumAmount) {
        this.sumAmount = sumAmount;
    }

    public void setUniqueMerchants(Set<String> uniqueMerchants) {
        this.uniqueMerchants = new HashSet<>(uniqueMerchants);
    }
}
