package com.example.tamengtautan;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.Locale;

public class TamengDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "tameng_history.db";
    // ⬆️ Naikkan versi karena kita tambahin normalisasi + migrasi NAIVE_BAYES → GAUSSIAN_NB
    private static final int DATABASE_VERSION = 3;

    public static final String TABLE_HISTORY       = "scan_history";
    public static final String COL_ID              = "_id";
    public static final String COL_TIMESTAMP       = "timestamp";      // millis
    public static final String COL_APP_PACKAGE     = "app_package";    // com.whatsapp
    public static final String COL_ORIGINAL_URL    = "original_url";   // yang discan user (bisa short)
    public static final String COL_RESOLVED_URL    = "resolved_url";   // hasil unshorten (boleh null)
    public static final String COL_HOST            = "host";           // docs.google.com, dll
    public static final String COL_RISK_SCORE      = "risk_score";     // float 0..1
    public static final String COL_RISK_LABEL      = "risk_label";     // HIGH / LOW / dsb.
    public static final String COL_ALGORITHM       = "algorithm";      // XGBOOST / GAUSSIAN_NB / dst.

    private static final String SQL_CREATE_TABLE =
            "CREATE TABLE " + TABLE_HISTORY + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_TIMESTAMP + " INTEGER NOT NULL, " +
                    COL_APP_PACKAGE + " TEXT, " +
                    COL_ORIGINAL_URL + " TEXT, " +
                    COL_RESOLVED_URL + " TEXT, " +
                    COL_HOST + " TEXT, " +
                    COL_RISK_SCORE + " REAL, " +
                    COL_RISK_LABEL + " TEXT, " +
                    COL_ALGORITHM + " TEXT" +
                    ");";

    public TamengDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Versi 1 -> 2: tambahin kolom algorithm biar data lama nggak hilang
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_HISTORY +
                    " ADD COLUMN " + COL_ALGORITHM + " TEXT");
        }

        // Versi 2 -> 3: rapikan nama algoritma lama yang masih NAIVE_BAYES
        if (oldVersion < 3) {
            db.execSQL("UPDATE " + TABLE_HISTORY +
                    " SET " + COL_ALGORITHM + " = 'GAUSSIAN_NB' " +
                    "WHERE " + COL_ALGORITHM + " = 'NAIVE_BAYES' " +
                    "   OR " + COL_ALGORITHM + " = 'GAUSSIAN_NAIVE_BAYES'");
        }
    }

    // 🔒 Helper: normalisasi nama algoritma sebelum disimpan
    private String normalizeAlgorithmKey(String algorithm) {
        if (algorithm == null) return null;
        String key = algorithm.toUpperCase(Locale.ROOT);

        // Semua variasi Naive Bayes jadi satu: GAUSSIAN_NB
        if ("NAIVE_BAYES".equals(key) || "GAUSSIAN_NAIVE_BAYES".equals(key)) {
            return "GAUSSIAN_NB";
        }

        return key;
    }

    public void deleteHistoryById(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(
                TABLE_HISTORY,
                COL_ID + " = ?",
                new String[]{ String.valueOf(id) }
        );
    }

    public void deleteAllHistory() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_HISTORY, null, null);
    }

    /**
     * Simpan satu entri riwayat scan.
     */
    public long insertScanHistory(long timestamp,
                                  String appPackage,
                                  String originalUrl,
                                  String resolvedUrl,
                                  String host,
                                  float riskScore,
                                  String riskLabel,
                                  String algorithm) {

        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_TIMESTAMP, timestamp);
        values.put(COL_APP_PACKAGE, appPackage);
        values.put(COL_ORIGINAL_URL, originalUrl);
        values.put(COL_RESOLVED_URL, resolvedUrl);
        values.put(COL_HOST, host);
        values.put(COL_RISK_SCORE, riskScore);
        values.put(COL_RISK_LABEL, riskLabel);

        // ✅ selalu dinormalisasi sebelum disimpan
        String normalizedAlgo = normalizeAlgorithmKey(algorithm);
        values.put(COL_ALGORITHM, normalizedAlgo);

        return db.insert(TABLE_HISTORY, null, values);
    }

    // Ambil semua riwayat, urut dari yang terbaru
    public java.util.List<ScanHistoryItem> getAllHistory() {
        java.util.List<ScanHistoryItem> list = new java.util.ArrayList<>();

        android.database.sqlite.SQLiteDatabase db = getReadableDatabase();
        android.database.Cursor cursor = null;

        try {
            cursor = db.query(
                    TABLE_HISTORY,
                    null,
                    null,
                    null,
                    null,
                    null,
                    COL_TIMESTAMP + " DESC"
            );

            while (cursor.moveToNext()) {
                long id          = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID));
                long timestamp   = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP));
                String pkg       = cursor.getString(cursor.getColumnIndexOrThrow(COL_APP_PACKAGE));
                String original  = cursor.getString(cursor.getColumnIndexOrThrow(COL_ORIGINAL_URL));
                String resolved  = cursor.getString(cursor.getColumnIndexOrThrow(COL_RESOLVED_URL));
                String host      = cursor.getString(cursor.getColumnIndexOrThrow(COL_HOST));
                float score      = cursor.getFloat(cursor.getColumnIndexOrThrow(COL_RISK_SCORE));
                String label     = cursor.getString(cursor.getColumnIndexOrThrow(COL_RISK_LABEL));
                String algo      = null;
                int algoIndex = cursor.getColumnIndex(COL_ALGORITHM);
                if (algoIndex >= 0) {
                    algo = cursor.getString(algoIndex);
                }

                ScanHistoryItem item = new ScanHistoryItem(
                        id,
                        timestamp,
                        pkg,
                        original,
                        resolved,
                        host,
                        score,
                        label,
                        algo
                );
                list.add(item);
            }

        } finally {
            if (cursor != null) cursor.close();
        }

        return list;
    }
}