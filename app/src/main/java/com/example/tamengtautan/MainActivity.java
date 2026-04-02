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

import android.widget.ScrollView;
import androidx.core.text.HtmlCompat;

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

            // 1. Buat ScrollView agar teks yang panjang bisa digeser (di-scroll)
            ScrollView scrollView = new ScrollView(this);
            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            int padding = (int) (24 * getResources().getDisplayMetrics().density);
            layout.setPadding(padding, padding, padding, padding);

            // 2. Siapkan Teks Kebijakan Privasi (Menggunakan format HTML agar ada tulisan tebal/bold)
            String privacyText = "<b>Kebijakan Privasi Tameng Tautan</b><br>" +
                    "Terakhir diperbarui: 1 April 2026<br><br>" +
                    "<b>1. Pendahuluan</b><br>" +
                    "Tameng Tautan (\"kami\") berkomitmen untuk melindungi privasi Anda. Kebijakan Privasi ini menjelaskan bagaimana kami mengumpulkan, menggunakan, dan melindungi informasi Anda saat menggunakan aplikasi Android Tameng Tautan. Aplikasi ini dibuat dan dikembangkan secara eksklusif HANYA sebagai sarana pengumpulan data untuk penelitian akademis (skripsi).<br><br>" +
                    "<b>2. Penggunaan Layanan Aksesibilitas (AccessibilityService API)</b><br>" +
                    "Aplikasi kami membutuhkan dan menggunakan <b>AccessibilityService API</b> murni untuk menjalankan fungsi utamanya, yaitu keamanan.<br>" +
                    "• <b>Tujuan:</b> Layanan ini bertugas untuk membaca dan memindai teks berupa tautan (URL) yang muncul di layar perangkat Anda secara <i>real-time</i>. Hal ini sangat penting agar aplikasi dapat langsung mendeteksi dan memunculkan peringatan pop-up jika tautan tersebut terindikasi sebagai ancaman <i>phishing</i>.<br>" +
                    "• <b>Batasan:</b> Kami TIDAK menggunakan layanan ini untuk membaca isi pesan obrolan, mengambil kata sandi, merekam ketikan keyboard, atau memata-matai aktivitas layar Anda. Layanan ini dirancang khusus hanya untuk menangkap pola tautan (URL) saja.<br><br>" +
                    "<b>3. Informasi yang Kami Kumpulkan dan Tujuannya (Khusus Skripsi)</b><br>" +
                    "Karena aplikasi ini adalah instrumen penelitian skripsi, kami wajib mengumpulkan log data performa deteksi ke dalam basis data penelitian kami. Kami sangat transparan mengenai data apa saja yang kami ambil. Data tersebut meliputi:<br>" +
                    "• <b>ID Pengguna / Perangkat Anonim (user_id):</b> ID acak untuk membedakan sesi pengujian antarpengguna tanpa mengetahui identitas asli (nama, nomor telepon, atau email) Anda.<br>" +
                    "• <b>Tautan (url):</b> Tautan yang Anda pindai secara manual atau yang tertangkap otomatis dari layar Anda.<br>" +
                    "• <b>Metrik Hasil Deteksi:</b> Kami merekam waktu pemindaian (created_at), status bahaya (is_phishing), tingkat persentase risiko (probability), algoritma <i>machine learning</i> yang mengeksekusinya (model_used), dan jenis pemindaian (scan_type: manual atau otomatis).<br>" +
                    "• <b>Tujuan Tunggal:</b> Semua data di atas HANYA digunakan untuk mengukur akurasi, efisiensi, dan kinerja model algoritma pendeteksi tautan dalam rangka penulisan laporan skripsi. Data ini TIDAK PERNAH dan TIDAK AKAN digunakan untuk pelacakan identitas (tracking), pemasaran, atau iklan komersial apa pun.<br><br>" +
                    "<b>4. Penyimpanan dan Pembagian Data</b><br>" +
                    "Kami tidak menjual, menyewakan, atau membagikan data log pemindaian maupun ID Perangkat Anda kepada pihak ketiga atau perusahaan mana pun. Data ini hanya dikelola secara tertutup oleh peneliti (mahasiswa) yang bersangkutan dan dijamin kerahasiaan akademisnya. Setelah penelitian skripsi selesai, seluruh data log ini dapat dihapus.<br><br>" +
                    "<b>5. Keamanan (Security)</b><br>" +
                    "Data Anda dikirim secara aman ke basis data <i>cloud</i> penelitian kami. Kami menerapkan standar keamanan elektronik yang wajar untuk melindungi informasi Anda selama transmisi. Namun, harap dipahami bahwa tidak ada transmisi internet yang 100% aman tanpa celah.<br><br>" +
                    "<b>6. Izin Pengguna</b><br>" +
                    "Anda memegang kendali mutlak atas aplikasi ini. Fitur deteksi tidak akan berjalan tanpa persetujuan Anda. Anda juga dapat mematikan fitur deteksi atau mencabut izin Aksesibilitas serta izin Overlay (Tampilan di Atas Aplikasi Lain) kapan saja melalui menu Pengaturan di perangkat Android Anda.<br><br>" +
                    "<b>7. Perubahan Kebijakan Ini</b><br>" +
                    "Kami dapat memperbarui Kebijakan Privasi ini jika ada perubahan pada metode penelitian kami. Anda disarankan untuk meninjau halaman ini secara berkala.<br><br>" +
                    "<b>8. Hubungi Kami</b><br>" +
                    "Jika Anda memiliki pertanyaan tentang praktik privasi ini, atau ingin bertanya seputar penelitian skripsi yang sedang dijalankan, jangan ragu untuk menghubungi peneliti melalui email yang tertera di halaman Google Play Store.";

            TextView message = new TextView(this);
            // Konversi format HTML menjadi teks yang bisa dibaca Android
            message.setText(HtmlCompat.fromHtml(privacyText, HtmlCompat.FROM_HTML_MODE_LEGACY));
            message.setTextSize(14f);

            // Masukkan teks ke dalam layout, lalu masukkan layout ke ScrollView
            layout.addView(message);
            scrollView.addView(layout);

            // 3. Tampilkan Dialog
            new MaterialAlertDialogBuilder(this)
                    .setView(scrollView)
                    .setPositiveButton("Buka di Web", (dialog, which) -> {
                        // Jika ditekan, baru buka link web
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW);
                        browserIntent.setData(Uri.parse("https://tamengtautan.vercel.app/privacypolicy"));
                        startActivity(browserIntent);
                    })
                    .setNegativeButton("Tutup", null) // Tutup pop-up saja
                    .show();

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