package com.example.tamengtautan;

public enum DetectionModel {
    XGBOOST("xgboost", "xgb_model.onnx"),
    DECISION_TREE("decision_tree", "decision_tree.onnx"),
    KNN("knn", "knn_phishing.onnx"),
    GAUSSIAN_NB("gaussian_nb", "gaussian_nb_phishing.onnx");

    private final String prefValue;
    private final String assetFileName;

    DetectionModel(String prefValue, String assetFileName) {
        this.prefValue = prefValue;
        this.assetFileName = assetFileName;
    }

    public String getPrefValue() {
        return prefValue;
    }

    public String getAssetFileName() {
        return assetFileName;
    }

    public static DetectionModel fromPrefValue(String value) {
        if (value == null) return XGBOOST;
        switch (value) {
            case "decision_tree":
                return DECISION_TREE;
            case "knn":
                return KNN;
            case "gaussian_nb":
                return GAUSSIAN_NB;
            case "xgboost":
            default:
                return XGBOOST;
        }
    }
}