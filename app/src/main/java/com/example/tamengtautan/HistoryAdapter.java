package com.example.tamengtautan;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.text.DateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    public interface SelectionListener {
        void onSelectionModeChanged(boolean enabled);
        void onSelectionCountChanged(int count);
    }

    private final Context context;
    private final PackageManager pm;
    private final DateFormat dateFormat;
    private final TamengDbHelper dbHelper;
    private final SelectionListener selectionListener;

    // daftar yang sedang ditampilkan di RecyclerView (boleh berubah karena filter)
    private final List<ScanHistoryItem> items;

    // state selection
    private final Set<Long> selectedIds = new HashSet<>();
    private boolean selectionMode = false;

    public HistoryAdapter(Context context,
                          List<ScanHistoryItem> items,
                          TamengDbHelper dbHelper,
                          SelectionListener listener) {
        this.context = context;
        this.items = items;
        this.dbHelper = dbHelper;
        this.selectionListener = listener;

        this.pm = context.getPackageManager();
        this.dateFormat = DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT,
                Locale.getDefault()
        );
    }

    // ==== RecyclerView ====

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_history, parent, false);
        return new HistoryViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        ScanHistoryItem item = items.get(position);

        // App name + icon
        String appName = item.appPackage;
        Drawable icon = context.getResources().getDrawable(R.mipmap.ic_launcher);
        if (item.appPackage != null) {
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(item.appPackage, 0);
                appName = pm.getApplicationLabel(appInfo).toString();
                icon = pm.getApplicationIcon(item.appPackage);
            } catch (Exception ignored) {
            }
        }
        holder.ivAppIcon.setImageDrawable(icon);
        holder.tvAppName.setText(appName);

        // waktu
        String timeText = dateFormat.format(new Date(item.timestamp));
        holder.tvTime.setText(timeText);

        // skor pill
        String pillText;
        if ("HIGH".equalsIgnoreCase(item.riskLabel)) {
            pillText = "HIGH";
        } else {
            pillText = "LOW";
        }
        holder.tvScorePill.setText(pillText);

        // URL original
        holder.tvOriginalUrl.setText(item.originalUrl != null ? item.originalUrl : "-");

        // URL final (kalau beda)
        if (item.resolvedUrl != null
                && !item.resolvedUrl.isEmpty()
                && !item.resolvedUrl.equals(item.originalUrl)) {
            holder.tvResolvedUrl.setVisibility(View.VISIBLE);
            holder.tvResolvedUrl.setText("→ " + item.resolvedUrl);
        } else {
            holder.tvResolvedUrl.setVisibility(View.GONE);
        }


        // host + algoritma
        String hostPart = (item.host != null && !item.host.isEmpty())
                ? item.host
                : "(host tidak diketahui)";

        String algoRaw = (item.algorithm != null && !item.algorithm.isEmpty())
                ? item.algorithm
                : "";

        // Mapping nama algoritma yang mau ditampilkan di card
        String algoPart;
        if (algoRaw.isEmpty()) {
            algoPart = "UNKNOWN";
        } else {
            switch (algoRaw.toUpperCase(Locale.ROOT)) {
                case "NAIVE_BAYES":
                    // entah di DB NAIVE_BAYES atau GAUSSIAN_NB,
                    // di UI selalu kita tulis GAUSSIAN_NB
                case "GAUSSIAN_NB":
                    algoPart = "GAUSSIAN_NB";
                    break;

                default:
                    algoPart = algoRaw;
                    break;
            }
        }

        holder.tvHostAlgorithm.setText(hostPart + " • " + algoPart);

        // === mode pilih ===
        boolean isChecked = selectionMode && selectedIds.contains(item.id);
        holder.cbSelect.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        holder.cbSelect.setChecked(isChecked);

        // klik biasa
        holder.itemView.setOnClickListener(v -> {
            if (selectionMode) {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    toggleSelection(pos);
                }
            }
        });

        // long-click untuk masuk mode pilih
        holder.itemView.setOnLongClickListener(v -> {
            if (!selectionMode) {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    enterSelectionMode();
                    toggleSelection(pos);
                    return true;
                }
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ==== ViewHolder ====

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAppIcon;
        TextView tvAppName;
        TextView tvTime;
        TextView tvScorePill;
        TextView tvOriginalUrl;
        TextView tvResolvedUrl;
        TextView tvHostAlgorithm;
        MaterialCheckBox cbSelect;   // checkbox di pinggir card

        HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAppIcon       = itemView.findViewById(R.id.ivAppIcon);
            tvAppName       = itemView.findViewById(R.id.tvAppName);
            tvTime          = itemView.findViewById(R.id.tvTime);
            tvScorePill     = itemView.findViewById(R.id.tvScorePill);
            tvOriginalUrl   = itemView.findViewById(R.id.tvOriginalUrl);
            tvResolvedUrl   = itemView.findViewById(R.id.tvResolvedUrl);
            tvHostAlgorithm = itemView.findViewById(R.id.tvHostAlgorithm);
            cbSelect        = itemView.findViewById(R.id.cbSelect);
        }
    }

    // ==== API untuk filter (dipanggil HistoryFragment) ====

    /** Dipakai saat filter berubah: ganti isi list & reset selection. */
    public void updateItems(List<ScanHistoryItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        // reset selection
        selectedIds.clear();
        selectionMode = false;

        if (selectionListener != null) {
            selectionListener.onSelectionModeChanged(false);
            selectionListener.onSelectionCountChanged(0);
        }

        notifyDataSetChanged();
    }

    // ==== API selection (hapus) ====

    public int getSelectedCount() {
        return selectedIds.size();
    }

    public void enterSelectionMode() {
        if (!selectionMode) {
            selectionMode = true;
            notifyDataSetChanged();
            if (selectionListener != null) {
                selectionListener.onSelectionModeChanged(true);
            }
        }
    }

    public void exitSelectionMode() {
        if (selectionMode) {
            selectionMode = false;
            selectedIds.clear();
            notifyDataSetChanged();
            if (selectionListener != null) {
                selectionListener.onSelectionModeChanged(false);
                selectionListener.onSelectionCountChanged(0);
            }
        }
    }

    private void toggleSelection(int position) {
        if (position < 0 || position >= items.size()) return;

        ScanHistoryItem item = items.get(position);
        if (selectedIds.contains(item.id)) {
            selectedIds.remove(item.id);
        } else {
            selectedIds.add(item.id);
        }
        notifyItemChanged(position);

        if (selectionListener != null) {
            selectionListener.onSelectionCountChanged(selectedIds.size());
        }

        // kalau setelah toggle, tidak ada yang terpilih → keluar dari selection mode
        if (selectedIds.isEmpty()) {
            exitSelectionMode();
        }
    }

    /** Dipanggil setelah user konfirmasi "hapus X item". */
    public void removeSelectedItems() {
        if (selectedIds.isEmpty()) return;

        // hapus dari DB + list (pakai iterator biar aman)
        java.util.Iterator<ScanHistoryItem> it = items.iterator();
        while (it.hasNext()) {
            ScanHistoryItem item = it.next();
            if (selectedIds.contains(item.id)) {
                if (dbHelper != null) {
                    dbHelper.deleteHistoryById(item.id);
                }
                it.remove();
            }
        }

        selectedIds.clear();
        selectionMode = false;

        if (selectionListener != null) {
            selectionListener.onSelectionModeChanged(false);
            selectionListener.onSelectionCountChanged(0);
        }

        notifyDataSetChanged();
    }

    // ===== helper untuk selection =====

    /** true kalau semua item di list sekarang sedang terpilih. */
    public boolean isAllSelected() {
        return !items.isEmpty() && selectedIds.size() == items.size();
    }

    /** Hapus semua centang tetapi tetap bertahan di selection mode. */
    public void clearSelectionOnly() {
        if (selectedIds.isEmpty()) return;
        selectedIds.clear();
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    /** Panggil listener setiap ada perubahan selection (mode / jumlah). */
    private void notifySelectionChanged() {
        if (selectionListener != null) {
            selectionListener.onSelectionModeChanged(selectionMode);
            selectionListener.onSelectionCountChanged(selectedIds.size());
        }
    }

    public void selectAll() {
        selectedIds.clear();
        for (ScanHistoryItem item : items) {
            selectedIds.add(item.id);
        }
        notifyDataSetChanged();
        notifySelectionChanged();
    }
}