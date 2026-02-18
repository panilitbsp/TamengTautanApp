package com.example.tamengtautan;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

public class UrlOnnxClassifier {

    private static final String TAG = "UrlOnnxClassifier";

    private final OrtEnvironment env;
    private OrtSession session;
    private final FeatureSpec featureSpec;
    private final Context appContext;

    // model yang sedang aktif + nama input ONNX-nya
    private DetectionModel currentModel;
    private String currentInputName = "input"; // default untuk XGBoost lama

    // ====== DAFTAR DOMAIN TERPERCAYA (bisa kamu tambah sendiri) ======
    private static final Set<String> TRUSTED_DOMAINS_EXACT = new HashSet<>(Arrays.asList(
            // social & chat
            "whatsapp.com",
            "www.whatsapp.com",
            "wa.me",
            "instagram.com",
            "www.instagram.com",
            "facebook.com",
            "www.facebook.com",
            "twitter.com",
            "www.twitter.com",
            "x.com",
            "www.x.com",
            "t.me",
            "telegram.me",
            "line.me",

            // ecommerce populer
            "shopee.co.id",
            "s.shopee.co.id",
            "tokopedia.com",
            "www.tokopedia.com",
            "bukalapak.com",
            "www.bukalapak.com",
            "blibli.com",
            "www.blibli.com",

            // platform umum lain
            "youtube.com",
            "www.youtube.com",
            "linktr.ee"
    ));

    public UrlOnnxClassifier(Context context, DetectionModel model) throws IOException, OrtException {
        this.appContext = context.getApplicationContext();

        // 1. Load feature_spec.json
        String json = AssetUtils.readAssetFile(appContext, "feature_spec.json");
        featureSpec = new Gson().fromJson(json, FeatureSpec.class);

        // 2. Init ONNX Runtime
        env = OrtEnvironment.getEnvironment();

        // 3. Load model pertama kali
        loadModel(model);
    }

    // Overload lama, biar kode lama (kalau ada) tetap jalan
    public UrlOnnxClassifier(Context context) throws IOException, OrtException {
        this(context, DetectionModel.XGBOOST);
    }

    public DetectionModel getCurrentModel() {
        return currentModel;
    }

    /**
     * Ganti / load ulang model ONNX sesuai enum yang dipilih user.
     */
    public void loadModel(DetectionModel model) throws IOException, OrtException {
        // kalau sama, gak usah reload
        if (model == currentModel && session != null) return;

        // tutup session lama kalau ada
        if (session != null) {
            try {
                session.close();
            } catch (Exception ignore) {}
            session = null;
        }

        currentModel = model;

        // tentukan nama file & input name ONNX
        String assetFile = model.getAssetFileName();
        if (model == DetectionModel.XGBOOST) {
            currentInputName = "input";        // sesuai model XGBoost lama
        } else {
            currentInputName = "float_input";  // sesuai initial_type di skl2onnx (RF)
        }

        byte[] modelBytes = loadModelFromAssets(appContext, assetFile);
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        session = env.createSession(modelBytes, options);

        Log.d(TAG, "Model ONNX ter-load: " + model.name()
                + " | asset=" + assetFile
                + " | inputName=" + currentInputName);
    }

    private byte[] loadModelFromAssets(Context context, String fileName) throws IOException {
        InputStream is = context.getAssets().open(fileName);
        byte[] buffer = new byte[is.available()];
        int read = is.read(buffer);
        is.close();
        return buffer;
    }

    // ==== Struktur hasil yang dikembalikan ke luar ====

    public static class Result {
        public final float probPhishing;  // probabilitas dari model (0–1)
        public final String riskLabel;    // "HIGH" / "LOW"
        public final float threshold;     // threshold konsep skripsi

        public Result(float probPhishing, String riskLabel, float threshold) {
            this.probPhishing = probPhishing;
            this.riskLabel = riskLabel;
            this.threshold = threshold;
        }

        @Override
        public String toString() {
            return "Result{" +
                    "probPhishing=" + probPhishing +
                    ", riskLabel='" + riskLabel + '\'' +
                    ", threshold=" + threshold +
                    '}';
        }
    }

    // ==== Fungsi utama untuk klasifikasi URL ====

    public Result classifyUrl(String url) throws OrtException {
        if (session == null) {
            throw new OrtException("ONNX session belum diinisialisasi");
        }

        // 0. Ambil host / domain untuk aturan tambahan
        String host = extractHost(url);
        boolean trustedDomain = isTrustedDomain(host);

        // 1. Ekstrak fitur dari URL → float[feature_count]
        float[] featureArray = FeatureExtractor.extractFeatures(url, featureSpec);

        // 2. Bentuk tensor input shape (1, feature_count)
        long[] shape = new long[]{1L, featureSpec.feature_count};
        FloatBuffer fb = FloatBuffer.wrap(featureArray);

        float prob;
        try (OnnxTensor inputTensor =
                     OnnxTensor.createTensor(env, fb, shape);
             OrtSession.Result output =
                     session.run(Collections.singletonMap(currentInputName, inputTensor))) {

            prob = extractPhishingProbability(output);
        }

        // Kalau tetap gagal dapetin prob (NaN), fallback ke 0.0 biar nggak crash
        if (Float.isNaN(prob)) {
            Log.w(TAG, "Tidak bisa mengekstrak probabilitas, fallback 0.0");
            prob = 0.0f;
        }

        // 3. Threshold dari JSON (konsep skripsi, misal 0.4)
        float threshold = featureSpec.risk_threshold;

        // 4. Tentukan label HIGH / LOW dengan mempertimbangkan domain
        //    - prob < threshold  → LOW
        //    - prob >= threshold → HIGH
        //    - KECUALI kalau domain termasuk trusted dan prob tidak ekstrem,
        //      maka kita turunkan jadi LOW (supaya Shopee/IG/WA tidak
        //      langsung dianggap "bahaya").
        String label;
        if (prob < threshold) {
            label = "LOW";
        } else {
            // prob >= threshold
            if (trustedDomain && prob < 0.90f) {
                // "secara pola agak mencurigakan, tapi domain populer/terverifikasi"
                // Untuk keperluan UX, diturunkan ke LOW.
                label = "LOW";
            } else {
                label = "HIGH";
            }
        }

        Log.d(TAG,
                "[CLASSIFY] model=" + (currentModel != null ? currentModel.name() : "UNKNOWN")
                        + " url=" + url
                        + " host=" + host
                        + " trustedDomain=" + trustedDomain
                        + " prob_raw=" + prob
                        + " threshold=" + threshold
                        + " finalLabel=" + label);

        return new Result(prob, label, threshold);
    }

    // ================== HELPER DOMAIN ==================

    private String extractHost(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host == null) return "";
            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            return host;
        } catch (Exception e) {
            Log.w(TAG, "Gagal parse host dari url: " + url, e);
            return "";
        }
    }

    private boolean isTrustedDomain(String host) {
        if (host == null || host.isEmpty()) return false;

        String h = host.toLowerCase(Locale.ROOT);

        // Cocok dengan daftar exact
        if (TRUSTED_DOMAINS_EXACT.contains(h)) return true;

        // Semua domain .ac.id (kampus) & .co.id (komersial di Indonesia)
        if (h.endsWith(".ac.id")) return true;
        if (h.endsWith(".co.id")) return true;

        return false;
    }

    // ================== PARSING OUTPUT ONNX (TIDAK DIUBAH) ==================

    /**
     * Cari output probabilitas dari hasil ONNX.
     * Support:
     *  - float[][] / double[][] (ambil kolom kelas 1 jika ada 2 kolom)
     *  - float[] / double[]
     *  - Map (atau array of Map) untuk model-model sklearn (DecisionTree/KNN/GaussianNB)
     */
    private float extractPhishingProbability(OrtSession.Result output) throws OrtException {
        int outCount = output.size();

        Object backupLabelTensor = null;  // fallback kalau cuma ada label 0/1
        Float probCandidate = null;

        Log.d(TAG, "=== Mulai parsing output ONNX, outCount=" + outCount + " ===");

        for (int i = 0; i < outCount; i++) {
            OnnxValue val = output.get(i);
            Object raw = val.getValue();
            if (raw == null) continue;

            Log.d(TAG, "Output[" + i + "] raw type=" + raw.getClass().getName());

            // 1) Cek tensor numerik dulu
            if (raw instanceof float[][]) {
                float[][] arr = (float[][]) raw;
                if (arr.length > 0 && arr[0].length > 0) {
                    // kalau 2 kolom → [prob_legit, prob_phishing]
                    probCandidate = (arr[0].length == 1) ? arr[0][0] : arr[0][1];
                    Log.d(TAG, "Prob dari float[][] = " + probCandidate);
                    break;
                }
            } else if (raw instanceof double[][]) {
                double[][] arr = (double[][]) raw;
                if (arr.length > 0 && arr[0].length > 0) {
                    probCandidate = (float) ((arr[0].length == 1) ? arr[0][0] : arr[0][1]);
                    Log.d(TAG, "Prob dari double[][] = " + probCandidate);
                    break;
                }
            } else if (raw instanceof float[]) {
                float[] arr = (float[]) raw;
                if (arr.length > 0) {
                    probCandidate = (arr.length == 1) ? arr[0] : arr[1];
                    Log.d(TAG, "Prob dari float[] = " + probCandidate);
                    break;
                }
            } else if (raw instanceof double[]) {
                double[] arr = (double[]) raw;
                if (arr.length > 0) {
                    probCandidate = (float) ((arr.length == 1) ? arr[0] : arr[1]);
                    Log.d(TAG, "Prob dari double[] = " + probCandidate);
                    break;
                }
            }
            // 2) Kalau bukan tensor numerik → coba baca sebagai Map
            else {
                Float fromMap = tryExtractFromMap(raw);
                if (fromMap != null) {
                    probCandidate = fromMap;
                    Log.d(TAG, "Prob dari Map = " + probCandidate);
                    break;
                }
            }

            // 3) Simpan label 0/1 sebagai fallback
            if (backupLabelTensor == null &&
                    (raw instanceof long[] || raw instanceof long[][])) {
                backupLabelTensor = raw;
            }
        }

        // 4) Kalau ada probabilitas yang valid → pakai itu
        if (probCandidate != null) {
            return probCandidate;
        }

        // 5) Fallback: pakai label 0/1 → 0.0 atau 1.0
        if (backupLabelTensor instanceof long[]) {
            long label = ((long[]) backupLabelTensor)[0];
            float prob = (label == 1L) ? 1.0f : 0.0f;
            Log.d(TAG, "Fallback dari long[] label=" + label + " → prob=" + prob);
            return prob;
        } else if (backupLabelTensor instanceof long[][]) {
            long label = ((long[][]) backupLabelTensor)[0][0];
            float prob = (label == 1L) ? 1.0f : 0.0f;
            Log.d(TAG, "Fallback dari long[][] label=" + label + " → prob=" + prob);
            return prob;
        }

        Log.w(TAG, "Tidak ditemukan output probabilitas maupun label, default ke 0.0");
        return 0.0f;
    }

    /**
     * Coba ekstrak probabilitas kelas "1" dari struktur Map yang
     * dipakai skl2onnx: bisa Map sendiri, atau array of Map.
     */
    @SuppressWarnings("unchecked")
    private Float tryExtractFromMap(Object raw) {
        Map<?, ?> map = null;

        if (raw instanceof Map) {
            map = (Map<?, ?>) raw;
        } else if (raw instanceof Object[]) {
            Object[] arr = (Object[]) raw;
            if (arr.length > 0 && arr[0] instanceof Map) {
                map = (Map<?, ?>) arr[0];
            }
        }

        if (map == null || map.isEmpty()) return null;

        Log.d(TAG, "Map probability keys=" + map.keySet() + " raw=" + map);

        Object val = map.get(1L);
        if (val == null) val = map.get(1);      // Integer key
        if (val == null) val = map.get("1");    // String key

        // kalau tetap null, ambil entry terakhir (biasanya kelas 1)
        if (val == null) {
            for (Object key : map.keySet()) {
                val = map.get(key);
            }
        }

        if (val == null) {
            Log.w(TAG, "Map probability tidak punya value untuk kelas 1");
            return null;
        }

        // unwrap kalau masih OnnxValue di dalam Map
        if (val instanceof OnnxValue) {
            try {
                Object inner = ((OnnxValue) val).getValue();
                return unwrapProbInner(inner);
            } catch (OrtException e) {
                Log.e(TAG, "Gagal getValue dari OnnxValue dalam Map", e);
                return null;
            }
        } else {
            return unwrapProbInner(val);
        }
    }

    /**
     * Normalisasi berbagai bentuk value jadi Float probabilitas.
     * - Float / Double / Number
     * - float[] / double[] (kalau 2 elemen → ambil index 1 = kelas phising)
     */
    private Float unwrapProbInner(Object v) {
        if (v == null) return null;

        if (v instanceof Float) {
            return (Float) v;
        } else if (v instanceof Double) {
            return ((Double) v).floatValue();
        } else if (v instanceof Number) {
            return ((Number) v).floatValue();
        } else if (v instanceof float[]) {
            float[] arr = (float[]) v;
            if (arr.length == 0) return null;
            return (arr.length == 1) ? arr[0] : arr[1];
        } else if (v instanceof double[]) {
            double[] arr = (double[]) v;
            if (arr.length == 0) return null;
            return (float) ((arr.length == 1) ? arr[0] : arr[1]);
        }

        Log.w(TAG, "unwrapProbInner gagal, type=" + v.getClass().getName());
        return null;
    }
}