package com.example.tamengtautan;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

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