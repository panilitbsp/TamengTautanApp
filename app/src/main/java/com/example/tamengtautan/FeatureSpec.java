package com.example.tamengtautan;

public class FeatureSpec {
    public String[] feature_names;
    public int feature_count;
    public float risk_threshold;

    // --- DUA BARIS TAMBAHAN UNTUK MEMBACA RUMUS DARI tameng_config.json ---
    public float[] scaler_mean;
    public float[] scaler_scale;
    // ----------------------------------------------------------------------

    public FeatureSpec() {
    }
}