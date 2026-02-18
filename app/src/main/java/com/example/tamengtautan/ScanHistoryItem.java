package com.example.tamengtautan;

public class ScanHistoryItem {

    public final long id;
    public final long timestamp;
    public final String appPackage;
    public final String originalUrl;
    public final String resolvedUrl;
    public final String host;
    public final float riskScore;
    public final String riskLabel;
    public final String algorithm;

    public ScanHistoryItem(long id,
                           long timestamp,
                           String appPackage,
                           String originalUrl,
                           String resolvedUrl,
                           String host,
                           float riskScore,
                           String riskLabel,
                           String algorithm) {
        this.id = id;
        this.timestamp = timestamp;
        this.appPackage = appPackage;
        this.originalUrl = originalUrl;
        this.resolvedUrl = resolvedUrl;
        this.host = host;
        this.riskScore = riskScore;
        this.riskLabel = riskLabel;
        this.algorithm = algorithm;
    }
}