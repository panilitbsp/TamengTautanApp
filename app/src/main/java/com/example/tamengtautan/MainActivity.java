package com.example.tamengtautan;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

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

        // 🔥 TAMPILKAN DIALOG T&C SAAT PERTAMA KALI DIBUKA
        checkAndShowTermsAndConditions();
    }

    // 🔥 METHOD UNTUK MENAMPILKAN T&C
    private void checkAndShowTermsAndConditions() {
        SharedPreferences prefs = getSharedPreferences("TamengPrefs", MODE_PRIVATE);
        boolean isAgreed = prefs.getBoolean("tc_agreed", false);

        if (!isAgreed) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Persetujuan Pengumpulan Data")
                    .setMessage("Aplikasi TamengTautan adalah sarana penelitian (skripsi). Untuk berfungsi, aplikasi ini menggunakan Layanan Aksesibilitas guna membaca tautan (URL) di layar Anda secara real-time.\n\n" +
                            "Kami mengumpulkan data berupa:\n" +
                            "• Tautan (URL) yang terdeteksi.\n" +
                            "• ID Perangkat (Device ID) anonim.\n\n" +
                            "Data ini murni untuk analisis statistik skripsi. Lanjutkan jika Anda setuju, atau baca Kebijakan Privasi selengkapnya.")
                    .setPositiveButton("Setuju", (dialog, which) -> {
                        prefs.edit().putBoolean("tc_agreed", true).apply();
                    })
                    .setNeutralButton("Kebijakan Privasi", (dialog, which) -> {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://tamengtautan.vercel.app/privacypolicy"));
                        startActivity(i);
                    })
                    .setNegativeButton("Tolak & Keluar", (dialog, which) -> {
                        finishAffinity();
                    })
                    .setCancelable(false)
                    .show();
        }
    }

    // 🔥 MENGATUR MENU ICON PRIVACY DI HEADER
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