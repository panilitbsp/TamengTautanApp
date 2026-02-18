package com.example.tamengtautan;
public class SupabaseScanHistory {
    public String device_id;      // uuid dari tabel devices
    public String app_package;
    public String original_url;
    public String resolved_url;
    public String algorithm;
    public String risk_label;
    public String timestamp;      // kirim ISO string, atau biar default DB aja (boleh null)

    public SupabaseScanHistory(String deviceId,
                               String appPackage,
                               String originalUrl,
                               String resolvedUrl,
                               String algorithm,
                               String riskLabel,
                               String timestamp) {
        this.device_id = deviceId;
        this.app_package = appPackage;
        this.original_url = originalUrl;
        this.resolved_url = resolvedUrl;
        this.algorithm = algorithm;
        this.risk_label = riskLabel;
        this.timestamp = timestamp;
    }
}