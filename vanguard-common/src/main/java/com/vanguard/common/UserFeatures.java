package com.vanguard.common;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserFeatures {
    double txnCount1m;
    double sumAmount1m;
    double txnCount5m;
    double sumAmount5m;
    double uniqueMerchants1hr;
    double geoDistanceLast;
    double timeSinceLastTxnSec;
    double logAmount;
    double hourSin;
    double hourCos;
    double daySin;
    double dayCos;

    public static UserFeatures defaultValues() {
        return UserFeatures.builder()
            .txnCount1m(0.0)
            .sumAmount1m(0.0)
            .txnCount5m(0.0)
            .sumAmount5m(0.0)
            .uniqueMerchants1hr(0.0)
            .geoDistanceLast(0.0)
            .timeSinceLastTxnSec(0.0)
            .logAmount(0.0)
            .hourSin(0.0)
            .hourCos(0.0)
            .daySin(0.0)
            .dayCos(0.0)
            .build();
    }
}
