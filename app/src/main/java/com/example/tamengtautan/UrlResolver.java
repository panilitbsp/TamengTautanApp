package com.example.tamengtautan;

import android.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class UrlResolver {

    private static final String TAG = "UrlResolver";
    private static final int TIMEOUT_MS = 10000;
    private static final int MAX_REDIRECTS = 5;

    // Deteksi domain shortener
    public static boolean isShortener(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.contains("bit.ly")
                || lower.contains("s.id")
                || lower.contains("tinyurl.com")
                || lower.contains("t.co")
                || lower.contains("goo.gl");
    }

    /**
     * Coba resolve short link:
     * 1) Coba auto-follow redirect (InstanceFollowRedirects = true)
     * 2) Kalau masih sama → coba manual baca header Location
     * 3) Kalau tetap gagal → balikin URL awal apa adanya
     */
    public static String resolveFinalUrl(String urlString) {
        if (urlString == null || urlString.isEmpty()) return urlString;

        String original = urlString;

        try {
            // ------- 1. Coba auto-follow redirect -------
            String auto = followWithAutoRedirect(original);
            if (auto != null && !auto.equals(original)) {
                Log.d(TAG, "Auto-follow final URL: " + auto);
                return auto;
            }

            // ------- 2. Coba manual redirect (baca Location) -------
            String manual = followManually(original);
            if (manual != null && !manual.isEmpty() && !manual.equals(original)) {
                Log.d(TAG, "Manual-follow final URL: " + manual);
                return manual;
            }

            // Kalau dua-duanya nggak dapat apa-apa, fallback
            Log.w(TAG, "Tidak ada redirect yang bisa diikuti, pakai URL awal: " + original);
            return original;

        } catch (Exception e) {
            Log.w(TAG, "Gagal resolve short link: " + original, e);
            return original;
        }
    }

    // ================== IMPLEMENTASI DETAIL ==================

    private static String followWithAutoRedirect(String urlStr) throws IOException {
        String current = urlStr;

        URL url = new URL(current);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setInstanceFollowRedirects(true);      // biarkan sistem ikut redirect
        conn.setRequestMethod("GET");
        conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Android; TamengTautan/1.0)"
        );

        int code = conn.getResponseCode();
        String finalUrl = conn.getURL().toString(); // setelah auto-follow

        Log.d(TAG, "Auto-follow status=" + code + " finalUrl=" + finalUrl);
        conn.disconnect();

        return finalUrl;
    }

    private static String followManually(String urlStr) throws IOException {
        String current = urlStr;
        int redirects = 0;

        while (redirects < MAX_REDIRECTS) {
            URL url = new URL(current);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(false);   // manual
            conn.setRequestMethod("GET");
            conn.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Android; TamengTautan/1.0)"
            );

            int code = conn.getResponseCode();

            if (code >= 300 && code < 400) {
                String location = conn.getHeaderField("Location");
                if (location == null || location.isEmpty()) {
                    Log.w(TAG, "Redirect tanpa Location, stop di: " + current);
                    conn.disconnect();
                    break;
                }

                URL newUrl = new URL(url, location);   // handle relative
                String newUrlStr = newUrl.toString();
                Log.d(TAG, "Manual redirect " + redirects + ": " + current + " -> " + newUrlStr);

                current = newUrlStr;
                redirects++;
                conn.disconnect();
            } else {
                // Bukan 3xx lagi → anggap final
                Log.d(TAG, "Manual final URL: " + current + " (status " + code + ")");
                conn.disconnect();
                break;
            }
        }

        return current;
    }
}