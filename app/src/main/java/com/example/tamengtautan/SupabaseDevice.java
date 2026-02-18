package com.example.tamengtautan;

public class SupabaseDevice {
    public String device_name;
    public String device_model;
    public String android_version;
    public Boolean privacy_consent;

    // constructor enak dipanggil
    public SupabaseDevice(String name, String model, String androidVersion, boolean consent) {
        this.device_name = name;
        this.device_model = model;
        this.android_version = androidVersion;
        this.privacy_consent = consent;
    }
}
