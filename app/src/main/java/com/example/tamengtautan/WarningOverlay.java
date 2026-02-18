package com.example.tamengtautan;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public class WarningOverlay {

    private static final String TAG = "WarningOverlay";

    private final Context overlayContext;
    private final WindowManager windowManager;

    private View overlayView;
    private boolean isShowing = false;

    public WarningOverlay(Context baseContext) {
        overlayContext = new ContextThemeWrapper(baseContext, R.style.Theme_TamengTautan);
        windowManager = (WindowManager) overlayContext.getSystemService(Context.WINDOW_SERVICE);
    }

    public boolean canDrawOverlays() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(overlayContext);
        } else {
            return true;
        }
    }

    public void show(String packageName,
                     String originalUrl,
                     String resolvedUrl,
                     float prob,
                     String label,
                     long detectedAtMillis,
                     String host,
                     boolean isInstDomain,
                     boolean isGoogleDomain,
                     String algorithmUsed) {

        if (!canDrawOverlays()) return;

        Log.d(TAG, "show() original=" + originalUrl + " | resolved=" + resolvedUrl);

        if (overlayView == null) {
            LayoutInflater inflater = LayoutInflater.from(overlayContext);
            overlayView = inflater.inflate(R.layout.overlay_warning, null);

            MaterialButton btnDismiss = overlayView.findViewById(R.id.btnDismiss);
            MaterialButton btnDetail  = overlayView.findViewById(R.id.btnOpenApp);

            // Tombol TUTUP
            btnDismiss.setOnClickListener(v -> dismiss());

            // Tombol LIHAT DETAIL → buka tab Riwayat
            btnDetail.setOnClickListener(v -> {
                Intent intent = new Intent(overlayContext, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("open_tab", "history");
                overlayContext.startActivity(intent);
                dismiss();
            });
        }

        // ================== BIND VIEW (icon, teks, dsb.) ==================
        ImageView ivAppIcon      = overlayView.findViewById(R.id.ivAppIcon);
        TextView tvAppName       = overlayView.findViewById(R.id.tvAppName);
        TextView tvDetectionTime = overlayView.findViewById(R.id.tvDetectionTime);
        TextView tvUrl           = overlayView.findViewById(R.id.tvUrl);
        TextView tvProb          = overlayView.findViewById(R.id.tvProb);
        TextView tvAlgo          = overlayView.findViewById(R.id.tvAlgo);

        TextView tvTip1          = overlayView.findViewById(R.id.tvTip1);
        TextView tvTip2          = overlayView.findViewById(R.id.tvTip2);
        TextView tvTip3          = overlayView.findViewById(R.id.tvTip3);

        LinearLayout layoutResolved = overlayView.findViewById(R.id.layoutResolved);
        TextView tvToggleResolved   = overlayView.findViewById(R.id.tvToggleResolved);
        TextView tvResolvedUrl      = overlayView.findViewById(R.id.tvResolvedUrl);

        MaterialCheckBox cbTrustDomain = overlayView.findViewById(R.id.cbTrustDomain);

        // 1) App icon + nama
        PackageManager pm = overlayContext.getPackageManager();
        String appLabelText = "Unknown app";
        Drawable appIcon = overlayContext.getResources().getDrawable(R.mipmap.ic_launcher);

        if (packageName != null) {
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                appLabelText = pm.getApplicationLabel(appInfo).toString();
                appIcon = pm.getApplicationIcon(packageName);
            } catch (Exception ignored) {}
        }
        ivAppIcon.setImageDrawable(appIcon);
        tvAppName.setText(appLabelText);

        // 2) Waktu deteksi
        DateFormat df = DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT
        );
        String timeText = df.format(new Date(detectedAtMillis));
        tvDetectionTime.setText(timeText);

        // 3) URL utama yang ditampilkan (selalu yang dikirim user / short URL)
        String mainUrl = (originalUrl != null && !originalUrl.isEmpty())
                ? originalUrl
                : resolvedUrl;
        tvUrl.setText(mainUrl);

        // 3b) Expand / collapse link asli (hasil unshorten)
        if (layoutResolved != null && tvToggleResolved != null && tvResolvedUrl != null) {

            boolean hasResolved =
                    resolvedUrl != null && !resolvedUrl.isEmpty() &&
                            (originalUrl == null || !resolvedUrl.equals(originalUrl));

            if (hasResolved) {
                layoutResolved.setVisibility(View.VISIBLE);

                tvResolvedUrl.setText(resolvedUrl);
                tvResolvedUrl.setVisibility(View.GONE);
                tvToggleResolved.setText("Lihat link asli");

                tvToggleResolved.setOnClickListener(v -> {
                    if (tvResolvedUrl.getVisibility() == View.GONE) {
                        tvResolvedUrl.setVisibility(View.VISIBLE);
                        tvToggleResolved.setText("Sembunyikan link asli");
                    } else {
                        tvResolvedUrl.setVisibility(View.GONE);
                        tvToggleResolved.setText("Lihat link asli");
                    }
                });
            } else {
                // Tidak ada link unshorten yang beda → hide section
                layoutResolved.setVisibility(View.GONE);
                tvResolvedUrl.setVisibility(View.GONE);
                tvToggleResolved.setOnClickListener(null);
            }
        }

        // 4) Status pill (HIGH / LOW saja, tanpa persen)
        if (tvProb != null) {
            String safeLabel = (label != null)
                    ? label.toUpperCase(Locale.ROOT)
                    : "-";

            tvProb.setText(" " + safeLabel + " ");

            // HIGH = merah, LOW = hijau, lainnya abu
            if ("HIGH".equalsIgnoreCase(label)) {
                tvProb.setTextColor(0xFFC62828); // merah
            } else if ("LOW".equalsIgnoreCase(label)) {
                tvProb.setTextColor(0xFF2E7D32); // hijau
            } else {
                tvProb.setTextColor(0xFF555555); // abu
            }
        }

        // 4b) Algo pill kecil: "scanned with XGBOOST"
        if (tvAlgo != null) {
            if (algorithmUsed != null && !algorithmUsed.isEmpty()) {
                String prettyName;
                switch (algorithmUsed.toUpperCase(Locale.ROOT)) {

                    case "XGBOOST":
                        prettyName = "XGBoost";
                        break;

                    case "DECISION_TREE":
                        prettyName = "Decision Tree";
                        break;

                    case "KNN":
                        prettyName = "KNN";
                        break;

                    case "GAUSSIAN_NB":
                    case "GAUSSIAN_NAIVE_BAYES":
                    case "NAIVE_BAYES":      // <–– TAMBAHAN
                        prettyName = "Gaussian_NB";
                        break;

                    default:
                        prettyName = algorithmUsed;
                }
                tvAlgo.setText("scan with " + prettyName);
                tvAlgo.setVisibility(View.VISIBLE);
            } else {
                tvAlgo.setVisibility(View.GONE);
            }
        }

        // 5) Tips sesuai domain
        if (tvTip1 != null && tvTip2 != null && tvTip3 != null) {
            if (isInstDomain) {
                tvTip1.setText("Pastikan Anda mengenal pengirim dan konteks tautan ini.");
                tvTip2.setText("Periksa bahwa tautan benar berasal dari unit atau instansi resmi Anda.");
                tvTip3.setText("Jika ragu, konfirmasi lewat kanal internal resmi sebelum membuka tautan.");
            } else if (isGoogleDomain) {
                tvTip1.setText("Pastikan form ini benar dibuat oleh pihak yang Anda kenal.");
                tvTip2.setText("Periksa kembali pembuat dan judul dokumen sebelum mengisi.");
                tvTip3.setText("Jangan mengisi data sensitif (NIK, PIN, OTP) ke dalam formulir apa pun.");
            } else {
                tvTip1.setText("Jangan buka atau meneruskan tautan ini.");
                tvTip2.setText("Hapus pesan jika tidak yakin sumbernya.");
                tvTip3.setText("Hubungi pihak resmi lewat kanal yang terpercaya.");
            }
        }

        // 6) Checkbox whitelist domain
        if (cbTrustDomain != null) {
            if ((isGoogleDomain || isInstDomain) && host != null && !host.isEmpty()) {
                cbTrustDomain.setVisibility(View.VISIBLE);
                cbTrustDomain.setOnCheckedChangeListener(null);
                cbTrustDomain.setChecked(false);
                cbTrustDomain.setText("Tandai domain ini aman (" + host + ")");

                cbTrustDomain.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        UserWhitelist.addTrustedDomain(overlayContext, host);
                        dismiss();
                    }
                });
            } else {
                cbTrustDomain.setOnCheckedChangeListener(null);
                cbTrustDomain.setVisibility(View.GONE);
            }
        }

        // 7) Tampilkan overlay
        if (!isShowing) {
            int type;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                type = WindowManager.LayoutParams.TYPE_PHONE;
            }

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP;
            params.y = 120;

            windowManager.addView(overlayView, params);
            isShowing = true;
        }
    }

    public void dismiss() {
        if (overlayView != null && isShowing) {
            windowManager.removeView(overlayView);
            isShowing = false;
        }
    }
}