package com.example.tamengtautan;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 🔹 set toolbar sebagai ActionBar
        MaterialToolbar toolbar = findViewById(R.id.mainToolbar);
        setSupportActionBar(toolbar);

        bottomNav = findViewById(R.id.bottom_nav);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_detection) {
                loadFragment(new DetectionFragment());
                return true;
            } else if (id == R.id.nav_history) {
                loadFragment(new HistoryFragment());
                return true;
            }
            return false;
        });

        // Tentukan tab awal
        openInitialTabFromIntent(getIntent());

        // TAMPILKAN DIALOG T&C SAAT PERTAMA KALI DIBUKA
        checkAndShowTermsAndConditions();
    }

    // METHOD UNTUK MENAMPILKAN T&C
    private void checkAndShowTermsAndConditions() {
        SharedPreferences prefs = getSharedPreferences("TamengPrefs", MODE_PRIVATE);
        boolean isAgreed = prefs.getBoolean("tc_agreed", false);

        if (!isAgreed) {
            // Pembuatan layout secara dinamis untuk menyisipkan Checkbox
            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            int padding = (int) (24 * getResources().getDisplayMetrics().density);
            layout.setPadding(padding, padding, padding, padding);

            TextView message = new TextView(this);
            // FORMAT WAJIB: [Aplikasi] mengumpulkan [Data] untuk mengaktifkan [Fitur]
            message.setText("TamengTautan mengumpulkan dan mentransmisikan data Informasi Pribadi Lainnya (URL/tautan yang terdeteksi di layar) dan ID Perangkat (Device Identifiers) untuk mengaktifkan fitur deteksi phishing secara real-time guna melindungi Anda dari ancaman penipuan.\n\n" +
                    "PENGUNGKAPAN PENELITIAN:\n" +
                    "Seluruh data yang dikumpulkan digunakan secara anonim murni untuk keperluan penelitian skripsi (akademis) mengenai keamanan siber. Data dikirimkan secara aman melalui enkripsi HTTPS.\n\n" +
                    "Informasi selengkapnya dapat dibaca di dalam aplikasi ini pada menu Kebijakan Privasi.");
            message.setTextSize(15f);
            message.setTextColor(getResources().getColor(android.R.color.black));

            CheckBox checkBox = new CheckBox(this);
            checkBox.setText("Saya setuju dengan pengumpulan data untuk keperluan penelitian skripsi ini.");
            checkBox.setPadding(0, 20, 0, 0);

            layout.addView(message);
            layout.addView(checkBox);

            AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                    .setTitle("Persetujuan Data & Penelitian")
                    .setView(layout)
                    .setCancelable(false) // User tidak bisa klik di luar dialog untuk menutup
                    .setPositiveButton("Setuju", (d, which) -> {
                        prefs.edit().putBoolean("tc_agreed", true).apply();
                    })
                    .setNegativeButton("Keluar", (d, which) -> finishAffinity())
                    .create();

            dialog.show();

            // KUNCI TOMBOL: Tombol 'Setuju' mati sampai Checkbox dicentang
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(isChecked);
            });
        }
    }

    // ICON PRIVACY DI HEADER
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
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