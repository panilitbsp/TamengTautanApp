package com.example.tamengtautan;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;

import ai.onnxruntime.OrtException;

public class DetectionFragment extends Fragment {

    private static final String TAG = "DetectionFragment";

    // Key Preference untuk menyimpan nilai slider
    private static final String PREF_RISK_THRESHOLD = "pref_risk_threshold";

    // Key ini harus sama persis dengan di Service untuk on/off
    private static final String PREF_DETECTION_ENABLED = "pref_detection_enabled";

    // UI Components
    private MaterialSwitch switchDetection;
    private MaterialAutoCompleteTextView dropdownAlgorithm;
    private TextView tvThresholdInfo;
    private Slider sliderRisk; // 🔥 Variabel Slider Baru
    private TextView tvShortUrlResult;
    private TextInputEditText etShortUrl;
    private TextView tvDetectionStatus;
    private android.widget.ScrollView scrollDetection;
    private View rootView;

    private CompoundButton.OnCheckedChangeListener detectionListener;

    private final String[] algoLabels = new String[]{
            "XGBoost (utama)",
            "Decision Tree",
            "KNN (k=7, distance)",
            "Gaussian Naive Bayes"
    };

    private final String[] algoValues = new String[]{
            "xgboost",
            "decision_tree",
            "knn",
            "gaussian_nb"
    };

    private UrlOnnxClassifier manualClassifier = null;
    private DetectionModel manualLastModel = null;

    public DetectionFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_detection, container, false);

        rootView        = view;
        scrollDetection = view.findViewById(R.id.scrollDetection);

        // Binding UI
        switchDetection   = view.findViewById(R.id.switchDetection);
        dropdownAlgorithm = view.findViewById(R.id.dropdownAlgorithm);
        tvThresholdInfo   = view.findViewById(R.id.tvThresholdInfo);
        sliderRisk        = view.findViewById(R.id.sliderRisk); // 🔥 Binding Slider
        tvShortUrlResult  = view.findViewById(R.id.tvShortUrlResult);
        etShortUrl        = view.findViewById(R.id.etShortUrl);
        tvDetectionStatus = view.findViewById(R.id.tvDetectionStatus);
        MaterialButton btnCheckShortUrl = view.findViewById(R.id.btnCheckShortUrl);

        // Setup Logic
        setupAlgorithmDropdown();
        setupSliderLogic(); // 🔥 Setup Slider Baru
        setupShortLinkTest(btnCheckShortUrl);
        setupSwitchLogic();

        // Sinkronisasi status saat pertama buka
        syncSwitchStatus();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        syncSwitchStatus();
    }

    // ================== LOGIC SLIDER (BARU) ==================

    private void setupSliderLogic() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());

        // Ambil nilai tersimpan, default 0.5 jika belum ada
        float savedValue = prefs.getFloat(PREF_RISK_THRESHOLD, 0.5f);

        // Set posisi slider
        sliderRisk.setValue(savedValue);

        // Update teks keterangan awal
        updateThresholdText(savedValue);

        // Listener saat user menggeser slider
        sliderRisk.addOnChangeListener((slider, value, fromUser) -> {
            // Simpan ke SharedPreferences agar bisa dibaca oleh Service
            prefs.edit().putFloat(PREF_RISK_THRESHOLD, value).apply();

            // Update teks UI secara real-time
            updateThresholdText(value);
        });
    }

    private void updateThresholdText(float value) {
        // Logika teks deskriptif agar user paham
        String description;
        if (value <= 0.3f) {
            description = "Tinggi (Sering Memberi Peringatan)";
        } else if (value <= 0.6f) {
            description = "Sedang (Seimbang)";
        } else {
            description = "Rendah (Jarang Memberi Peringatan)";
        }

        // Format string 1 angka di belakang koma (misal: 0.5)
        String valStr = String.format("%.1f", value);
        tvThresholdInfo.setText("Batas: " + valStr + " — " + description);
    }

    // ================== DROPDOWN ALGORITMA ==================

    private void setupAlgorithmDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_list_item_1,
                algoLabels
        );
        dropdownAlgorithm.setAdapter(adapter);

        dropdownAlgorithm.setOnClickListener(v -> dropdownAlgorithm.showDropDown());
        dropdownAlgorithm.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) dropdownAlgorithm.showDropDown();
        });

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String currentValue = prefs.getString("pref_model_algorithm", "xgboost");

        int selectedIndex = 0;
        for (int i = 0; i < algoValues.length; i++) {
            if (algoValues[i].equals(currentValue)) {
                selectedIndex = i;
                break;
            }
        }

        dropdownAlgorithm.setText(algoLabels[selectedIndex], false);

        // CATATAN: Baris setText threshold manual DIHAPUS, karena sekarang diurus oleh setupSliderLogic

        dropdownAlgorithm.setOnItemClickListener((parent, view, position, id) -> {
            String value = algoValues[position];
            prefs.edit().putString("pref_model_algorithm", value).apply();
            Toast.makeText(
                    requireContext(),
                    "Algoritma diganti ke: " + algoLabels[position],
                    Toast.LENGTH_SHORT
            ).show();

            manualClassifier = null;
            manualLastModel = null;
        });
    }

    // ================== CEK & BUKA IZIN OVERLAY ==================

    private boolean canDrawOverlays() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(requireContext());
        } else {
            return true;
        }
    }

    private void openOverlaySettings() {
        try {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + requireContext().getPackageName())
            );
            startActivity(intent);
        } catch (Exception e) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            startActivity(intent);
        }
    }

    // ================== SWITCH + AKSESIBILITAS ==================

    private void setupSwitchLogic() {
        detectionListener = (buttonView, isChecked) -> {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            prefs.edit().putBoolean(PREF_DETECTION_ENABLED, isChecked).apply();

            if (isChecked) {
                tvDetectionStatus.setText("Deteksi real-time (AKTIF)");

                // 1. Cek Aksesibilitas
                if (!isAccessibilityServiceEnabled(requireContext(), TamengAccessibilityService.class)) {
                    showAccessibilityDisclosureDialog(); // Panggil Dialog Dulu!
                }
                // 2. Cek Overlay
                else if (!canDrawOverlays()) {
                    showOverlayDisclosureDialog(); // Panggil Dialog Dulu!
                }

            } else {
                tvDetectionStatus.setText("Deteksi real-time (NONAKTIF)");
            }
        };

        switchDetection.setOnCheckedChangeListener(detectionListener);
    }

    // 🔥 Tambahkan Method Dialog Aksesibilitas
    private void showAccessibilityDisclosureDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Izin Aksesibilitas Diperlukan")
                .setMessage("Aplikasi TamengTautan menggunakan layanan Aksesibilitas (AccessibilityService) untuk memindai dan membaca tautan (URL) yang muncul di layar Anda secara real-time. Hal ini bertujuan murni untuk mendeteksi bahaya phishing dan melindungi Anda.\n\nKami TIDAK mengumpulkan, menyimpan, atau menjual data pribadi Anda.")
                .setPositiveButton("Mengerti & Lanjutkan", (dialog, which) -> {
                    openAccessibilitySettings();
                })
                .setNegativeButton("Batal", (dialog, which) -> {
                    // Kembalikan switch ke posisi mati jika user menolak
                    switchDetection.setOnCheckedChangeListener(null);
                    switchDetection.setChecked(false);
                    switchDetection.setOnCheckedChangeListener(detectionListener);
                    tvDetectionStatus.setText("Deteksi real-time (NONAKTIF)");
                })
                .setCancelable(false)
                .show();
    }

    // 🔥 Tambahkan Method Dialog Overlay
    private void showOverlayDisclosureDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Izin Tampilkan di Atas Aplikasi Lain")
                .setMessage("TamengTautan membutuhkan izin ini untuk menampilkan pop-up peringatan berwarna merah langsung di layar Anda ketika tautan berbahaya terdeteksi.")
                .setPositiveButton("Mengerti & Lanjutkan", (dialog, which) -> {
                    openOverlaySettings();
                })
                .setNegativeButton("Batal", (dialog, which) -> {
                    switchDetection.setOnCheckedChangeListener(null);
                    switchDetection.setChecked(false);
                    switchDetection.setOnCheckedChangeListener(detectionListener);
                    tvDetectionStatus.setText("Deteksi real-time (NONAKTIF)");
                })
                .setCancelable(false)
                .show();
    }

    private boolean isAccessibilityServiceEnabled() {
        if (getContext() == null) return false;
        return isAccessibilityServiceEnabled(
                requireContext(),
                TamengAccessibilityService.class
        );
    }

    private void syncSwitchStatus() {
        if (getContext() == null) return;

        boolean systemEnabled = isAccessibilityServiceEnabled();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        boolean logicEnabled = prefs.getBoolean(PREF_DETECTION_ENABLED, true);

        switchDetection.setOnCheckedChangeListener(null);
        boolean finalState = systemEnabled && logicEnabled;
        switchDetection.setChecked(finalState);

        tvDetectionStatus.setText(
                finalState
                        ? "Deteksi real-time (AKTIF)"
                        : "Deteksi real-time (NONAKTIF)"
        );

        switchDetection.setOnCheckedChangeListener(detectionListener);
    }

    private boolean isAccessibilityServiceEnabled(Context context,
                                                  Class<? extends AccessibilityService> serviceClass) {
        String enabledServices =
                Settings.Secure.getString(
                        context.getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                );

        if (enabledServices == null || enabledServices.isEmpty()) {
            return false;
        }

        String serviceId = context.getPackageName() + "/" + serviceClass.getName();
        return enabledServices.toLowerCase().contains(serviceId.toLowerCase());
    }

    private void openAccessibilitySettings() {
        try {
            Intent detailIntent = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS");
            detailIntent.setData(Uri.parse("package:" + requireContext().getPackageName()));
            startActivity(detailIntent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
            } catch (Exception ex) {
                Toast.makeText(
                        requireContext(),
                        "Tidak dapat membuka pengaturan Aksesibilitas.",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    // ================== SHORT LINK TEST (UPDATE DENGAN SLIDER) ==================

    private UrlOnnxClassifier getManualClassifier(Context appContext) throws IOException, OrtException {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
        String value = prefs.getString("pref_model_algorithm", "xgboost");
        DetectionModel model = DetectionModel.fromPrefValue(value);

        if (manualClassifier == null || manualLastModel != model) {
            manualClassifier = new UrlOnnxClassifier(appContext, model);
            manualLastModel = model;
            Log.d(TAG, "Manual classifier dibuat ulang dengan model: " + model.name());
        }
        return manualClassifier;
    }

    private void scrollToView(final View target) {
        if (scrollDetection == null || target == null) return;
        scrollDetection.post(() -> {
            int[] location = new int[2];
            target.getLocationOnScreen(location);
            int targetTop = target.getTop();
            int y = targetTop - dpToPx(80);
            if (y < 0) y = 0;
            scrollDetection.smoothScrollTo(0, y);
        });
    }

    private int dpToPx(int dp) {
        Context ctx = getContext();
        if (ctx == null) return dp;
        float density = ctx.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    private void setupShortLinkTest(MaterialButton btnCheckShortUrl) {
        etShortUrl.setOnClickListener(v -> scrollToView(etShortUrl));
        etShortUrl.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) scrollToView(etShortUrl);
        });

        btnCheckShortUrl.setOnClickListener(v -> {
            String shortUrl = etShortUrl.getText() != null
                    ? etShortUrl.getText().toString().trim()
                    : "";

            if (shortUrl.isEmpty()) {
                tvShortUrlResult.setText("Masukkan link pendek terlebih dahulu.");
                return;
            }

            tvShortUrlResult.setText("Sedang mengurai dan memeriksa link...");

            Context appContext = requireContext().getApplicationContext();
            String currentAlgoLabel = dropdownAlgorithm.getText() != null
                    ? dropdownAlgorithm.getText().toString()
                    : "-";

            new Thread(() -> {
                try {
                    String resolved = UrlResolver.resolveFinalUrl(shortUrl);
                    boolean changed = resolved != null && !resolved.isEmpty() && !resolved.equals(shortUrl);
                    String finalUrl = (resolved != null && !resolved.isEmpty()) ? resolved : shortUrl;

                    UrlOnnxClassifier classifier = getManualClassifier(appContext);
                    UrlOnnxClassifier.Result rawResult = classifier.classifyUrl(finalUrl);

                    float prob = rawResult.probPhishing;
                    if (Float.isNaN(prob) || prob < 0f) prob = 0f;
                    if (prob > 1f) prob = 1f;
                    int percent = Math.round(prob * 100f);

                    // 🔥 UPDATE: Mengambil ambang batas dari SLIDER (Preference)
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
                    float currentThreshold = prefs.getFloat(PREF_RISK_THRESHOLD, 0.5f);

                    // Logic override manual untuk sekolah/google jika diperlukan bisa ditambah di sini,
                    // tapi untuk test manual biasanya kita pakai raw value slider saja agar user tahu efek slidernya.

                    String mappedLabel = (prob >= currentThreshold) ? "HIGH" : "LOW";

                    StringBuilder sb = new StringBuilder();
                    sb.append("URL awal (short link):\n")
                            .append(shortUrl)
                            .append("\n\n");

                    if (changed) {
                        sb.append("URL tujuan yang diperiksa:\n")
                                .append(finalUrl)
                                .append("\n\n");
                    } else {
                        sb.append("URL tujuan (Original):\n")
                                .append(finalUrl)
                                .append("\n\n");
                    }

                    sb.append("Algoritma: ").append(currentAlgoLabel).append("\n");
                    sb.append("Batas Risiko (Setting): ").append(String.format("%.1f", currentThreshold)).append("\n");
                    sb.append("Skor Prediksi: ")
                            .append(percent).append("% (")
                            .append(mappedLabel.equals("HIGH")
                                    ? "HIGH – berisiko"
                                    : "LOW – aman")
                            .append(")");

                    String finalText = sb.toString();

                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> tvShortUrlResult.setText(finalText));
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Gagal memeriksa short URL", e);
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() ->
                                tvShortUrlResult.setText(
                                        "Terjadi kesalahan saat memeriksa link.\n" +
                                                "Pastikan koneksi internet aktif lalu coba lagi."
                                )
                        );
                    }
                }
            }).start();
        });
    }
}