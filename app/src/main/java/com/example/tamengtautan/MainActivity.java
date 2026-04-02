package com.example.tamengtautan;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.CheckBox;
import androidx.appcompat.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private int currentNavId = R.id.nav_detection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.mainToolbar);
        setSupportActionBar(toolbar);

        bottomNav = findViewById(R.id.bottom_nav);

        bottomNav.setOnItemSelectedListener(item -> {
            currentNavId = item.getItemId();

            if (currentNavId == R.id.nav_detection) {
                loadFragment(new DetectionFragment());
                invalidateOptionsMenu();
                return true;
            } else if (currentNavId == R.id.nav_history) {
                loadFragment(new HistoryFragment());
                invalidateOptionsMenu();
                return true;
            }
            return false;
        });

        openInitialTabFromIntent(getIntent());
        checkAndShowAccessibilityDisclosure();
    }

    // Helper untuk mengecek apakah Accessibility Service sudah aktif atau belum
    private boolean isAccessibilityServiceEnabled() {
        int accessibilityEnabled = 0;
        final String service = getPackageName() + "/" + TamengAccessibilityService.class.getCanonicalName();
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    getApplicationContext().getContentResolver(),
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            // ignore
        }
        TextUtils.SimpleStringSplitter mStringColonSplitter = new TextUtils.SimpleStringSplitter(':');
        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(
                    getApplicationContext().getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue);
                while (mStringColonSplitter.hasNext()) {
                    String accessibilityService = mStringColonSplitter.next();
                    if (accessibilityService.equalsIgnoreCase(service)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // PERBAIKAN: Dialog Pengungkapan Jelas (Prominent Disclosure)
    private void checkAndShowAccessibilityDisclosure() {
        SharedPreferences prefs = getSharedPreferences("TamengPrefs", MODE_PRIVATE);
        boolean isAgreed = prefs.getBoolean("accessibility_agreed", false);

        // Hanya tampilkan jika user belum setuju DAN service belum aktif di pengaturan
        if (!isAgreed || !isAccessibilityServiceEnabled()) {
            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            int padding = (int) (24 * getResources().getDisplayMetrics().density);
            layout.setPadding(padding, padding, padding, padding);

            TextView message = new TextView(this);

            // TEKS WAJIB GOOGLE PLAY: Menyebutkan API, Data (ID Perangkat & Info Pribadi Lain), dan Tujuan
            message.setText("PENGUNGKAPAN PENGGUNAAN ACCESSIBILITY API\n\n" +
                    "TamengTautan menggunakan AccessibilityService API untuk dapat berfungsi dengan baik. \n\n" +
                    "Melalui layanan AccessibilityService API ini, aplikasi kami mengumpulkan data berupa:\n" +
                    "1. Info Pribadi Lainnya (URL atau tautan yang terdeteksi di layar Anda).\n" +
                    "2. ID Perangkat atau lainnya (Device Identifiers).\n\n" +
                    "Tujuan: Data ini dikumpulkan untuk mengaktifkan fitur perlindungan deteksi tautan phishing secara real-time dan untuk keperluan penelitian skripsi akademis mengenai keamanan siber. \n\n" +
                    "Data Anda ditransmisikan secara aman. Kami tidak mengubah setelan pengguna atau melewati kontrol privasi Anda.");
            message.setTextSize(14f);
            message.setTextColor(getResources().getColor(android.R.color.black));

            CheckBox checkBox = new CheckBox(this);
            checkBox.setText("Saya setuju menggunakan AccessibilityService API dan setuju dengan pengumpulan data tersebut.");
            checkBox.setPadding(0, 20, 0, 0);

            layout.addView(message);
            layout.addView(checkBox);

            AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                    .setTitle("Persetujuan Layanan & Data")
                    .setView(layout)
                    .setCancelable(false) // Tidak bisa klik di luar untuk menutup
                    .setPositiveButton("Setuju & Aktifkan", (d, which) -> {
                        prefs.edit().putBoolean("accessibility_agreed", true).apply();
                        // WAJIB: Setelah setuju, arahkan langsung ke pengaturan Android
                        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                        startActivity(intent);
                    })
                    .setNegativeButton("Tolak", (d, which) -> {
                        // 2 Tombol diwajibkan: Jika ditolak, aplikasi keluar
                        finishAffinity();
                    })
                    .create();

            dialog.show();

            // Kunci tombol "Setuju & Aktifkan" hingga checkbox dicentang
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(isChecked);
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem privacyItem = menu.findItem(R.id.action_privacy);
        if (privacyItem != null) {
            privacyItem.setVisible(currentNavId != R.id.nav_history);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_privacy) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://tamengtautan.vercel.app/privacypolicy"));
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openInitialTabFromIntent(intent);
    }

    private void openInitialTabFromIntent(Intent intent) {
        String openTab = null;
        if (intent != null) {
            openTab = intent.getStringExtra("open_tab");
        }

        if ("history".equals(openTab)) {
            bottomNav.setSelectedItemId(R.id.nav_history);
        } else {
            bottomNav.setSelectedItemId(R.id.nav_detection);
        }
    }

    private void loadFragment(@NonNull Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}