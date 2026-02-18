package com.example.tamengtautan;

public class SupabaseDeviceResponse {
    public String device_id;
    public String device_name;
    public String device_model;
    public String android_version;
    public Boolean privacy_consent;
    public String created_at;
}

class SupabaseScanHistoryResponse {
    public long id;
    public String device_id;
    // kalau mau tambahin field lain, boleh
}
