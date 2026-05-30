package com.vanguard.scoring.service;

import com.vanguard.common.Transaction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
public class FeatureExtractionService {

    public float[] toFeatureArray(Transaction txn) {
        double amt = txn.amount().doubleValue();
        double oldBalOrig = txn.oldBalanceOrig().doubleValue();
        double newBalOrig = txn.newBalanceOrig().doubleValue();
        double oldBalDest = txn.oldBalanceDest().doubleValue();
        double newBalDest = txn.newBalanceDest().doubleValue();

        double balanceDeltaOrig = oldBalOrig - newBalOrig;
        double balanceDeltaDest = newBalDest - oldBalDest;
        double origZeroAfter = newBalOrig == 0.0 ? 1.0 : 0.0;

        String type = txn.transactionType();
        boolean isHighRisk = "CASH_OUT".equals(type) || "TRANSFER".equals(type);

        double logAmt = Math.log(amt + 1) / Math.log(2);
        double amtToOrigRatio = oldBalOrig > 0 ? amt / oldBalOrig : 0.0;
        double logOrigBal = Math.log(oldBalOrig + 1) / Math.log(2);
        double logDestBal = Math.log(oldBalDest + 1) / Math.log(2);

        Instant ts = txn.timestamp();
        ZonedDateTime zdt = ts.atZone(ZoneId.of("UTC"));
        int hour = zdt.getHour();
        int dayOfWeek = zdt.getDayOfWeek().getValue();
        double hourSin = Math.sin(2 * Math.PI * hour / 24.0);
        double hourCos = Math.cos(2 * Math.PI * hour / 24.0);
        double daySin = Math.sin(2 * Math.PI * (dayOfWeek - 1) / 7.0);
        double dayCos = Math.cos(2 * Math.PI * (dayOfWeek - 1) / 7.0);

        return new float[]{
            (float) amt,
            (float) oldBalOrig,
            (float) newBalOrig,
            (float) oldBalDest,
            (float) newBalDest,
            "CASH_IN".equals(type) ? 1f : 0f,
            "CASH_OUT".equals(type) ? 1f : 0f,
            "DEBIT".equals(type) ? 1f : 0f,
            "PAYMENT".equals(type) ? 1f : 0f,
            "TRANSFER".equals(type) ? 1f : 0f,
            (float) balanceDeltaOrig,
            (float) balanceDeltaDest,
            (float) origZeroAfter,
            isHighRisk ? 1f : 0f,
            (float) logAmt,
            (float) amtToOrigRatio,
            (float) hourSin, (float) hourCos, (float) daySin, (float) dayCos,
            (float) logOrigBal,
            (float) logDestBal
        };
    }
}
