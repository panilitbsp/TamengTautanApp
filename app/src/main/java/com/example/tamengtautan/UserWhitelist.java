package com.example.tamengtautan;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class UserWhitelist {

    // key untuk SharedPreferences
    private static final String PREF_KEY_TRUSTED_DOMAINS = "user_trusted_domains";

    private static SharedPreferences getPrefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(
                context.getApplicationContext()
        );
    }

    /**
     * Ambil daftar domain yang ditandai aman oleh user.
     */
    public static Set<String> getTrustedDomains(Context context) {
        SharedPreferences prefs = getPrefs(context);
        Set<String> saved = prefs.getStringSet(
                PREF_KEY_TRUSTED_DOMAINS,
                Collections.emptySet()
        );
        // Bikin copy supaya aman kalau mau dimodifikasi
        return new HashSet<>(saved);
    }

    /**
     * Tambah domain yang user tandai sebagai aman.
     * Disimpan dalam bentuk lowercase, mis: "docs.google.com", "ditbangdik.itb.ac.id".
     */
    public static void addTrustedDomain(Context context, String domain) {
        if (domain == null) return;

        domain = domain.trim().toLowerCase(Locale.ROOT);
        if (domain.isEmpty()) return;

        SharedPreferences prefs = getPrefs(context);
        Set<String> current = getTrustedDomains(context);
        current.add(domain);

        prefs.edit()
                .putStringSet(PREF_KEY_TRUSTED_DOMAINS, current)
                .apply();
    }

    /**
     * Cek apakah URL / host ini termasuk ke domain yang ditandai aman oleh user.
     * - Kalau parameter berupa URL penuh → diambil host-nya dulu.
     * - Kalau parameter sudah host (tanpa http...) → langsung dicek.
     * Match:
     *   host == domain
     *   atau host berakhiran ".domain" (subdomain).
     */
    public static boolean isTrusted(Context context, String urlOrHost) {
        if (urlOrHost == null) return false;

        String host = urlOrHost;

        // Kalau mengandung "://" anggap ini URL lengkap
        if (urlOrHost.contains("://")) {
            host = UrlUtils.getHost(urlOrHost);
        }

        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);

        Set<String> trusted = getTrustedDomains(context);
        for (String d : trusted) {
            if (host.equals(d) || host.endsWith("." + d)) {
                return true;
            }
        }
        return false;
    }
}