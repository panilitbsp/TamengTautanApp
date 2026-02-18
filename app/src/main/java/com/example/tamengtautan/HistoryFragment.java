package com.example.tamengtautan;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HistoryFragment extends Fragment implements HistoryAdapter.SelectionListener {

    // ==== UI utama ====
    private RecyclerView rvHistory;
    private TextView tvEmpty;

    // bottom bar multi delete
    private View bottomDeleteBar;
    private TextView tvSelectedCount;
    private TextView tvSelectAll;
    private MaterialButton btnDeleteSelected;
    private ImageButton btnCancelSelection;

    // filter summary + chips
    private TextView tvFilterSummary;
    private LinearLayout layoutFilterChips;

    // badge angka di icon filter (action view)
    private TextView tvFilterBadge;

    // ==== Data & helper ====
    private TamengDbHelper dbHelper;
    private HistoryAdapter adapter;

    // semua data dari DB (full list)
    private List<ScanHistoryItem> allItems = new ArrayList<>();

    // state filter
    // true  = terbaru → terlama
    // false = terlama → terbaru
    private boolean sortNewestFirst = true;
    private final Set<String> selectedAlgorithms = new LinkedHashSet<>();

    // Database
    private static final String PREFS_NAME = "tameng_prefs";
    private static final String KEY_DEVICE_ID = "supabase_device_id";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ==== findViewById ====
        rvHistory        = view.findViewById(R.id.rvHistory);
        tvEmpty          = view.findViewById(R.id.tvEmpty);

        bottomDeleteBar  = view.findViewById(R.id.bottomDeleteBar);
        tvSelectedCount  = view.findViewById(R.id.tvSelectedCount);
        tvSelectAll      = view.findViewById(R.id.tvSelectAll);
        btnDeleteSelected = view.findViewById(R.id.btnDeleteSelected);
        btnCancelSelection = view.findViewById(R.id.btnCancelSelection);

        tvFilterSummary  = view.findViewById(R.id.tvFilterSummary);
        layoutFilterChips = view.findViewById(R.id.layoutFilterChips);

        rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        dbHelper = new TamengDbHelper(requireContext());

        // ==== bottom bar actions ====

        // tombol HAPUS (hapus item yang dipilih)
        btnDeleteSelected.setOnClickListener(v -> {
            if (adapter == null) return;
            int count = adapter.getSelectedCount();
            if (count == 0) return;

            new AlertDialog.Builder(requireContext())
                    .setTitle("Hapus riwayat")
                    .setMessage("Benar mau menghapus " + count + " item?")
                    .setPositiveButton("Hapus", (dialog, which) -> {
                        adapter.removeSelectedItems();

                        if (adapter.getItemCount() == 0) {
                            tvEmpty.setVisibility(View.VISIBLE);
                            rvHistory.setVisibility(View.GONE);
                        }
                    })
                    .setNegativeButton("Batal", null)
                    .show();
        });

        // tombol PILIH SEMUA (toggle)
        tvSelectAll.setOnClickListener(v -> {
            if (adapter == null) return;

            if (adapter.isAllSelected()) {
                // kalau semua sudah terpilih → klik lagi = unselect all (tetap di selection mode)
                adapter.clearSelectionOnly();
            } else {
                // kalau belum semua → masuk mode pilih & select all
                adapter.enterSelectionMode();
                adapter.selectAll();
            }
        });

        // tombol X di kiri PILIH SEMUA -> keluar dari selection mode + clear
        btnCancelSelection.setOnClickListener(v -> {
            if (adapter != null) {
                adapter.exitSelectionMode();
            }
        });

        loadHistory();
    }

    // ================== LOAD + FILTER ==================

    private void loadHistory() {
        allItems = dbHelper.getAllHistory();
        if (allItems == null) {
            allItems = new ArrayList<>();
        }

        if (adapter == null) {
            adapter = new HistoryAdapter(
                    requireContext(),
                    new ArrayList<>(),   // list awal, nanti diisi lewat applyFilterAndUpdateUI
                    dbHelper,
                    this
            );
            rvHistory.setAdapter(adapter);
        }

        applyFilterAndUpdateUI();
    }

    private void applyFilterAndUpdateUI() {
        if (allItems == null) return;

        // 1) filter algoritma
        List<ScanHistoryItem> filtered = new ArrayList<>();
        for (ScanHistoryItem item : allItems) {
            if (!selectedAlgorithms.isEmpty()) {
                // normalisasi key dari DB
                String algoKey = normalizeAlgorithmKey(item.algorithm);
                if (!selectedAlgorithms.contains(algoKey)) {
                    continue;
                }
            }
            filtered.add(item);
        }

        // 2) sort
        if (sortNewestFirst) {
            // terbaru → terlama
            Collections.sort(filtered, (a, b) -> Long.compare(b.timestamp, a.timestamp));
        } else {
            // terlama → terbaru
            Collections.sort(filtered, (a, b) -> Long.compare(a.timestamp, b.timestamp));
        }

        // 3) kirim ke adapter
        adapter.updateItems(filtered);

        // 4) empty / list visibility
        if (filtered.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvHistory.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvHistory.setVisibility(View.VISIBLE);
        }

        // 5) update summary + chips + badge
        updateFilterSummaryAndChips(filtered.size());
        updateFilterBadge();
    }

    private int getActiveFilterCount() {
        int count = 0;
        // sort TERLAMA dianggap 1 filter
        if (!sortNewestFirst) count += 1;
        count += selectedAlgorithms.size();
        return count;
    }

    private void updateFilterBadge() {
        if (tvFilterBadge == null) return;
        int count = getActiveFilterCount();
        if (count <= 0) {
            tvFilterBadge.setVisibility(View.GONE);
        } else {
            tvFilterBadge.setVisibility(View.VISIBLE);
            tvFilterBadge.setText(String.valueOf(count));
        }
    }

    private void updateFilterSummaryAndChips(int currentCount) {
        if (tvFilterSummary == null || layoutFilterChips == null) return;

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        layoutFilterChips.removeAllViews();

        boolean hasSortFilter = !sortNewestFirst;
        boolean hasAlgoFilter = !selectedAlgorithms.isEmpty();

        if (!hasSortFilter && !hasAlgoFilter) {
            tvFilterSummary.setText("Menampilkan " + currentCount + " data");
            return;
        } else {
            tvFilterSummary.setText("Menampilkan " + currentCount + " data sesuai filter");
        }

        // chip sort (kalau pakai TERLAMA)
        if (hasSortFilter) {
            addFilterChip(inflater, "Terlama", v -> {
                sortNewestFirst = true;     // balik ke default (TERBARU)
                applyFilterAndUpdateUI();
            });
        }

        // chip algoritma
        for (String algoKey : new ArrayList<>(selectedAlgorithms)) {
            String display = getDisplayAlgorithmName(algoKey);
            addFilterChip(inflater, display, v -> {
                // hapus pakai key internal, bukan label display
                selectedAlgorithms.remove(algoKey);
                applyFilterAndUpdateUI();
            });
        }
    }

    private void addFilterChip(LayoutInflater inflater, String label, View.OnClickListener onClose) {
        Chip chip = (Chip) inflater.inflate(R.layout.item_filter_chip, layoutFilterChips, false);
        chip.setText(label);
        chip.setOnCloseIconClickListener(onClose);
        layoutFilterChips.addView(chip);
    }

    // ================== MENU (ICON FILTER + DELETE ATAS) ==================

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_history, menu);

        // action view untuk filter (icon + badge)
        MenuItem filterItem = menu.findItem(R.id.action_filter);
        View actionView = filterItem.getActionView();
        if (actionView != null) {
            tvFilterBadge = actionView.findViewById(R.id.tvFilterBadge);
            actionView.setOnClickListener(v -> showFilterDialog());
            updateFilterBadge();
        } else {
            tvFilterBadge = null;
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_cloud) {
            // backup ke Supabase
            if (allItems == null || allItems.isEmpty()) {
                android.widget.Toast.makeText(
                        requireContext(),
                        "Belum ada data untuk dikirim.",
                        android.widget.Toast.LENGTH_SHORT
                ).show();
            } else {
                new AlertDialog.Builder(requireContext())
                        .setTitle("Izinkan backup untuk skripsi?")
                        .setMessage(
                                "Riwayat link dan info perangkat akan dikirim ke server Supabase " +
                                        "milik peneliti hanya untuk keperluan skripsi.\n" +
                                        "Tidak ada isi chat yang disimpan.\n\n" +
                                        "Jumlah data yang akan dikirim: " + allItems.size() + " riwayat."
                        )
                        .setPositiveButton("Setuju", (dialog, which) -> uploadDeviceIfNeeded())
                        .setNegativeButton("Batal", null)
                        .show();
            }
            return true;

        } else if (id == R.id.action_filter) {
            showFilterDialog();
            return true;

        } else if (id == R.id.action_delete) {
            if (adapter != null) {
                adapter.enterSelectionMode();
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    private void showFilterDialog() {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View dialogView = inflater.inflate(R.layout.dialog_filter_history, null);

        MaterialCheckBox cbSortNewest      = dialogView.findViewById(R.id.cbSortNewest);
        MaterialCheckBox cbSortOldest      = dialogView.findViewById(R.id.cbSortOldest);
        MaterialCheckBox cbAlgoXgboost     = dialogView.findViewById(R.id.cbAlgoXgboost);
        MaterialCheckBox cbAlgoDecision    = dialogView.findViewById(R.id.cbAlgoDecisionTree);
        MaterialCheckBox cbAlgoKnn         = dialogView.findViewById(R.id.cbAlgoKnn);
        MaterialCheckBox cbAlgoNaive       = dialogView.findViewById(R.id.cbAlgoNaiveBayes);
        TextView tvClearAll                = dialogView.findViewById(R.id.tvClearAllFilter);

        // state awal
        cbSortNewest.setChecked(sortNewestFirst);
        cbSortOldest.setChecked(!sortNewestFirst);

        cbAlgoXgboost.setChecked(selectedAlgorithms.contains("XGBOOST"));
        cbAlgoDecision.setChecked(selectedAlgorithms.contains("DECISION_TREE"));
        cbAlgoKnn.setChecked(selectedAlgorithms.contains("KNN"));
        cbAlgoNaive.setChecked(selectedAlgorithms.contains("GAUSSIAN_NB"));

        // sort hanya boleh satu
        cbSortNewest.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                cbSortOldest.setChecked(false);
            } else if (!cbSortOldest.isChecked()) {
                cbSortNewest.setChecked(true);
            }
        });

        cbSortOldest.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                cbSortNewest.setChecked(false);
            } else if (!cbSortNewest.isChecked()) {
                cbSortOldest.setChecked(true);
            }
        });

        // hapus semua filter
        tvClearAll.setOnClickListener(v -> {
            cbSortNewest.setChecked(true);
            cbSortOldest.setChecked(false);

            cbAlgoXgboost.setChecked(false);
            cbAlgoDecision.setChecked(false);
            cbAlgoKnn.setChecked(false);
            cbAlgoNaive.setChecked(false);
        });

        new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("Terapkan", (dialog, which) -> {
                    // simpan state
                    sortNewestFirst = cbSortNewest.isChecked();

                    selectedAlgorithms.clear();
                    if (cbAlgoXgboost.isChecked())    selectedAlgorithms.add("XGBOOST");
                    if (cbAlgoDecision.isChecked())   selectedAlgorithms.add("DECISION_TREE");
                    if (cbAlgoKnn.isChecked())        selectedAlgorithms.add("KNN");
                    if (cbAlgoNaive.isChecked())      selectedAlgorithms.add("GAUSSIAN_NB");

                    applyFilterAndUpdateUI();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    // Normalisasi key algoritma supaya NAIVE_BAYES & GAUSSIAN_NB dianggap sama
    private String normalizeAlgorithmKey(String algo) {
        if (algo == null) return "";
        String key = algo.toUpperCase(Locale.ROOT);
        if ("NAIVE_BAYES".equals(key)) {
            return "GAUSSIAN_NB";
        }
        return key;
    }

    // Nama yang ditampilkan di chip
    private String getDisplayAlgorithmName(String algoKey) {
        if (algoKey == null || algoKey.isEmpty()) return "UNKNOWN";

        switch (algoKey.toUpperCase(Locale.ROOT)) {
            case "NAIVE_BAYES":
            case "GAUSSIAN_NB":
                return "GAUSSIAN_NB";
            default:
                return algoKey;
        }
    }

    // ================== SUPABASE BACKUP ==================

    private void uploadDeviceIfNeeded() {
        String saved = getDeviceIdFromPrefs();
        if (saved != null && !saved.isEmpty()) {
            // sudah pernah kirim device sebelumnya → langsung upload history
            uploadHistory(saved);
            return;
        }

        // belum punya device_id di Supabase → insert baru
        String deviceName = android.os.Build.MANUFACTURER;
        String deviceModel = android.os.Build.MODEL;
        String androidVersion = android.os.Build.VERSION.RELEASE;

        SupabaseDevice dev = new SupabaseDevice(
                deviceName,
                deviceModel,
                androidVersion,
                true   // user barusan consent
        );

        SupabaseApi api = SupabaseClient.getApi();
        java.util.List<SupabaseDevice> body = new java.util.ArrayList<>();
        body.add(dev);

        api.insertDevice(body).enqueue(new retrofit2.Callback<java.util.List<SupabaseDeviceResponse>>() {
            @Override
            public void onResponse(
                    retrofit2.Call<java.util.List<SupabaseDeviceResponse>> call,
                    retrofit2.Response<java.util.List<SupabaseDeviceResponse>> response) {

                if (response.isSuccessful()
                        && response.body() != null
                        && !response.body().isEmpty()) {

                    String deviceId = response.body().get(0).device_id;
                    saveDeviceIdToPrefs(deviceId);
                    uploadHistory(deviceId);

                } else {
                    showToast("Gagal menyimpan info perangkat ke Supabase.");
                }
            }

            @Override
            public void onFailure(
                    retrofit2.Call<java.util.List<SupabaseDeviceResponse>> call,
                    Throwable t) {
                showToast("Gagal konek Supabase: " + t.getMessage());
            }
        });
    }

    private void uploadHistory(String deviceId) {
        if (allItems == null || allItems.isEmpty()) {
            showToast("Tidak ada riwayat untuk dikirim.");
            return;
        }

        java.util.List<SupabaseScanHistory> payload = new java.util.ArrayList<>();

        for (ScanHistoryItem item : allItems) {
            // Normalisasi nama algoritma sebelum dikirim
            String algoKey = normalizeAlgorithmKey(item.algorithm);

            SupabaseScanHistory row = new SupabaseScanHistory(
                    deviceId,
                    item.appPackage,
                    item.originalUrl,
                    item.resolvedUrl,
                    algoKey,           // <--- sudah dipaksa GAUSSIAN_NB kalau tadinya NAIVE_BAYES
                    item.riskLabel,
                    null   // timestamp → biar Supabase isi sendiri
            );
            payload.add(row);
        }

        SupabaseApi api = SupabaseClient.getApi();
        api.insertScanHistory(payload).enqueue(new retrofit2.Callback<java.util.List<SupabaseScanHistoryResponse>>() {
            @Override
            public void onResponse(
                    retrofit2.Call<java.util.List<SupabaseScanHistoryResponse>> call,
                    retrofit2.Response<java.util.List<SupabaseScanHistoryResponse>> response) {

                if (response.isSuccessful()) {
                    showToast("Berhasil mengirim " + payload.size() + " data ke Supabase.");
                } else {
                    showToast("Gagal kirim riwayat (kode " + response.code() + ").");
                }
            }

            @Override
            public void onFailure(
                    retrofit2.Call<java.util.List<SupabaseScanHistoryResponse>> call,
                    Throwable t) {
                showToast("Gagal konek Supabase: " + t.getMessage());
            }
        });
    }

    private void saveDeviceIdToPrefs(String id) {
        requireContext()
                .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_DEVICE_ID, id)
                .apply();
    }

    private String getDeviceIdFromPrefs() {
        return requireContext()
                .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .getString(KEY_DEVICE_ID, null);
    }

    private void showToast(String msg) {
        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show();
    }

    // ================== CALLBACK DARI ADAPTER (mode pilih) ==================

    @Override
    public void onSelectionModeChanged(boolean enabled) {
        if (bottomDeleteBar != null) {
            bottomDeleteBar.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onSelectionCountChanged(int count) {
        if (tvSelectedCount != null) {
            tvSelectedCount.setText(count + " dipilih");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        rvHistory = null;
        tvEmpty = null;
        bottomDeleteBar = null;
        tvSelectedCount = null;
        tvSelectAll = null;
        btnDeleteSelected = null;
        tvFilterSummary = null;
        layoutFilterChips = null;
        tvFilterBadge = null;
    }
}