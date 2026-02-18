package com.example.tamengtautan;

import android.net.Uri;

import java.util.Locale;

public class UrlUtils {

    /**
     * Ambil host dari URL (tanpa protocol & path), dalam huruf kecil.
     * Contoh:
     *  - https://docs.google.com/forms/.. -> docs.google.com
     *  - http://ditbangdik.itb.ac.id/page -> ditbangdik.itb.ac.id
     */
    public static String getHost(String url) {
        if (url == null || url.trim().isEmpty()) return null;

        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host == null || host.isEmpty()) return null;
            return host.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Ambil "registrable domain" sederhana.
     * Bukan super akurat seperti lib public suffix, tapi cukup buat kasus skripsi:
     *
     *  - sub.domain.ums.ac.id -> ums.ac.id
     *  - ditbangdik.itb.ac.id -> itb.ac.id
     *  - mail.google.com -> google.com
     */
    public static String getRegistrableDomain(String host) {
        if (host == null) return null;

        host = host.toLowerCase(Locale.ROOT).trim();
        String[] parts = host.split("\\.");
        if (parts.length <= 2) {
            return host;
        }

        String last = parts[parts.length - 1];       // com / id / ac / go
        String second = parts[parts.length - 2];     // google / umd / itb / etc.

        // Heuristik: kalau TLD dua huruf (id, uk, jp), kemungkinan perlu 3 label terakhir
        if (last.length() == 2 && parts.length >= 3) {
            String third = parts[parts.length - 3];
            return third + "." + second + "." + last;    // misal itb.ac.id / ums.ac.id
        } else {
            return second + "." + last;                  // misal google.com
        }
    }
}