package com.example.tamengtautan;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.preference.PreferenceManager;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TamengAccessibilityService extends AccessibilityService {

    // database
    private TamengDbHelper dbHelper;

    private static final String TAG = "TamengAccessibility";

    // 🔥 PERUBAHAN 1: Key Preference (harus sama dengan Fragment)
    private static final String PREF_DETECTION_ENABLED = "pref_detection_enabled";

    // 🔥 PERUBAHAN 2: Key Preference untuk Slider Ambang Batas
    private static final String PREF_RISK_THRESHOLD = "pref_risk_threshold";

    // Helper untuk cek status toggle
    private boolean isDetectionEnabled() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        // Default true (aktif) jika belum pernah diset
        return prefs.getBoolean(PREF_DETECTION_ENABLED, true);
    }

    // Paket yang selalu di-skip (keyboard, browser, launcher, SystemUI, dll.)
    private static final String[] IGNORE_PACKAGES = new String[]{
            // keyboard
            "com.google.android.inputmethod.latin",
            "com.google.android.inputmethod.latin.dev",
            "com.samsung.android.honeyboard",

            // browser / pencarian / video
            "com.android.chrome",
            "com.google.android.googlequicksearchbox",
            "com.google.android.youtube",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "com.sec.android.app.sbrowser",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.brave.browser",
            "com.android.browser",

            // launcher & system UI
            "com.sec.android.app.launcher",
            "com.android.systemui"
    };

    // Pola URL (http/https OPTIONAL + TLD bebas)
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(" +
                    "(?:https?://)?" +
                    "[\\w\\-._~%]+" +
                    "\\." +
                    "[A-Za-z]{2,}" +
                    "(?::\\d{2,5})?" +
                    "(?:/[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]*)?" +
                    ")",
            Pattern.CASE_INSENSITIVE
    );

    // Hint sederhana supaya kita cuma scan teks yang bener-bener mirip URL
    private static final String[] URL_TEXT_HINTS = new String[]{
            "http://",
            "https://",
            ".com",
            ".co.id",
            ".ac.id",
            ".sch.id",
            ".edu",
            "bit.ly/",
            "s.id/",
            "tinyurl.com/",
            "wa.me/",
            "t.co/",
            "forms.gle/"
    };

    /**
     * Kalau teks sama sekali nggak mengandung pola ini,
     * kita anggap itu cuma chat biasa (misal: "apdet...potone").
     */
    private boolean looksLikeUrlCandidate(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String hint : URL_TEXT_HINTS) {
            if (lower.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    // HAPUS KONSTANTA LAMA (PHISHING_THRESHOLD) agar tidak bingung
    // private static final float PHISHING_THRESHOLD = 0.4f;

    // Cooldown global per URL
    private static final long URL_COOLDOWN_MS = 10_000L;      // 10 detik
    // Debounce sebelum dikirim ke executor
    private static final long URL_DEBOUNCE_MS = 1_000L;       // 1 detik
    // Cooldown untuk URL yang sama di layar / room yang sama
    private static final long SAME_SCREEN_COOLDOWN_MS = 5_000L; // 5 detik

    // Anti-spam state
    private String lastShownUrlKey = null;
    private String lastShownPackage = null;
    private long lastShownTime = 0L;

    private final Map<String, Long> urlWarningHistory = new ConcurrentHashMap<>();
    private final Map<String, Long> urlRecentSeen     = new ConcurrentHashMap<>();
    private final Map<String, UrlOnnxClassifier.Result> urlResultCache = new ConcurrentHashMap<>();

    private static final boolean DEBUG_FORCE_TEST_HIGH = false;

    private UrlOnnxClassifier urlClassifier;
    private WarningOverlay warningOverlay;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler;

    private DetectionModel lastModel = null;

    // ================== PREFS / MODEL ==================
    private DetectionModel getSelectedModelFromPrefs() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String value = prefs.getString("pref_model_algorithm", "xgboost");
        return DetectionModel.fromPrefValue(value);
    }

    private void ensureModelUpToDate() {
        if (urlClassifier == null) return;

        DetectionModel current = getSelectedModelFromPrefs();
        if (current != lastModel) {
            try {
                urlClassifier.loadModel(current);
                lastModel = current;
                Log.d(TAG, "Model diganti ke: " + current.name());
            } catch (Exception e) {
                Log.e(TAG, "Gagal ganti model ke: " + current.name(), e);
            }
        }
    }

    private String buildUrlKey(String url) {
        if (url == null) return "";
        try {
            android.net.Uri uri = android.net.Uri.parse(url);
            String host = uri.getHost();
            String path = uri.getPath();
            if (host == null) host = "";
            if (path == null) path = "";
            return host.toLowerCase(Locale.ROOT) + "|" + path;
        } catch (Exception e) {
            return url;
        }
    }

    // ================== LIFECYCLE ==================

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "TamengAccessibilityService terhubung");

        mainHandler = new Handler(Looper.getMainLooper());
        warningOverlay = new WarningOverlay(this);

        // 🔹 inisialisasi DB helper
        dbHelper = new TamengDbHelper(this);

        try {
            DetectionModel initial = getSelectedModelFromPrefs();
            lastModel = initial;
            urlClassifier = new UrlOnnxClassifier(this, initial);
            Log.d(TAG, "UrlOnnxClassifier siap dengan model: " + initial.name());
        } catch (Exception e) {
            Log.e(TAG, "Gagal inisialisasi UrlOnnxClassifier", e);
        }
    }

    @Override
    public void onInterrupt() {
        // no-op
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }

    // ================== HELPER PACKAGE / DOMAIN ==================

    private boolean isIgnoredPackage(String pkgName) {
        if (pkgName == null) return false;

        // Jangan scan app sendiri
        if (pkgName.equals(getPackageName())) return true;

        for (String p : IGNORE_PACKAGES) {
            if (pkgName.equals(p)) {
                return true;
            }
        }
        return false;
    }

    private boolean isKeyboardPackage(String pkgName) {
        if (pkgName == null) return false;

        if (pkgName.startsWith("com.google.android.inputmethod")) return true;
        if (pkgName.startsWith("com.android.inputmethod")) return true;
        if (pkgName.contains("swiftkey")) return true;
        if (pkgName.contains("honeyboard")) return true;
        if (pkgName.contains("keyboard")) return true;

        return false;
    }

    private boolean isTrustedInstitutionDomain(String host) {
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);

        if (host.endsWith(".ums.ac.id") || host.equals("ums.ac.id")) return true;
        if (host.endsWith(".kemdikbud.go.id")) return true;

        return false;
    }

    private boolean isAcademicOrSchoolDomain(String host) {
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);

        return host.endsWith(".ac.id")
                || host.endsWith(".sch.id")
                || host.endsWith(".edu")
                || host.endsWith(".edu.id")
                || host.endsWith(".school")
                || host.endsWith(".academy")
                || host.endsWith(".institute");
    }

    private boolean isGoogleProductDomain(String host) {
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);

        if (host.equals("docs.google.com")) return true;
        if (host.equals("drive.google.com")) return true;
        if (host.equals("forms.gle")) return true;

        // Tambahan: layanan Google umum
        if (host.equals("gmail.com")) return true;
        if (host.equals("mail.google.com")) return true;
        if (host.endsWith(".google.com")) return true;

        return false;
    }

    /**
     * Anggap aman: domain sederhana "nama.com" / "nama.co.id" / "nama.ac.id"
     * (tanpa subdomain panjang).
     * Contoh aman: gmail.com, tiktok.com, bca.co.id, ums.ac.id
     * Contoh TIDAK masuk: secure.login-bank.com, abc.def.ums.ac.id
     */
    private boolean isSafeSimpleComLikeDomain(String host) {
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);

        // Pola: satu nama + TLD yang kita anggap aman
        return host.matches("^[a-z0-9\\-]+\\.(com|co\\.id|ac\\.id)$");
    }

    // ================== DETEKSI ROOM CHAT (HYBRID ID + STRICT GEOMETRY) ==================

    /**
     * Memeriksa apakah layar saat ini adalah room chat dengan 2 metode:
     * 1. Cek ID Spesifik (Paling Akurat untuk WA/Google Messages/Telegram)
     * 2. Heuristik Ketat (Cadangan untuk aplikasi lain)
     */
    private boolean isProbableChatRoomScreen(String pkgName) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        // 🚀 METODE 1: Cek ID Spesifik (VIP)
        // Jika ketemu ID input kolom chat yang valid, langsung TRUE.
        if (isSpecificAppChatInput(root, pkgName)) {
            Log.d(TAG, "Pkg " + pkgName + " isProbableChatRoomScreen = TRUE (By ID)");
            return true;
        }

        // SAFETY VALVE (PENGAMAN KEBOCORAN)
        // Jika ini adalah aplikasi yang SUDAH KITA KENAL ID-nya (WA, Samsung, Google Msg),
        // tapi ID-nya TIDAK KETEMU di atas, berarti kita pasti ada di LIST VIEW atau menu lain.
        // MAKA: JANGAN LANJUT KE HEURISTIK. Langsung return FALSE.
        if (pkgName.equals("com.samsung.android.messaging") ||
                pkgName.equals("com.whatsapp") ||
                pkgName.equals("com.whatsapp.w4b") ||
                pkgName.equals("com.google.android.apps.messaging") ||
                pkgName.equals("org.telegram.messenger")) {

            // Log.d(TAG, "Pkg " + pkgName + " ID tidak ketemu, skip heuristik (mungkin di List View).");
            return false;
        }

        // METODE 2: Heuristik Geometri (Hanya untuk aplikasi asing yang belum terdaftar)
        Rect rootRect = new Rect();
        root.getBoundsInScreen(rootRect);
        int screenHeight = rootRect.height();
        if (screenHeight <= 0) {
            screenHeight = getResources().getDisplayMetrics().heightPixels;
        }

        boolean result = hasBottomMessageInput(root, screenHeight, 0);

        if (result) {
            Log.d(TAG, "Pkg " + pkgName + " isProbableChatRoomScreen = TRUE (By Heuristic)");
        }

        return result;
    }

    /**
     * Daftar ID kolom chat untuk aplikasi populer.
     * Ini mencegah salah deteksi di halaman list (karena halaman list beda ID).
     */
    private boolean isSpecificAppChatInput(AccessibilityNodeInfo root, String pkgName) {
        if (pkgName == null) return false;

        String targetId = null;

        switch (pkgName) {
            case "com.whatsapp":
            case "com.whatsapp.w4b":
                targetId = "com.whatsapp:id/entry";
                break;

            case "com.google.android.apps.messaging":
                targetId = "com.google.android.apps.messaging:id/compose_message_text";
                break;

            case "org.telegram.messenger":
            case "org.telegram.plus":
                targetId = "org.telegram.messenger:id/chat_text_view";
                break;

            // 🔥 TAMBAHAN PENTING: ID SAMSUNG MESSAGES 🔥
            case "com.samsung.android.messaging":
                // Cek beberapa kemungkinan ID Samsung (beda HP kadang beda ID)

                // Kemungkinan 1 (OneUI Terbaru)
                List<AccessibilityNodeInfo> s1 = root.findAccessibilityNodeInfosByViewId("com.samsung.android.messaging:id/composer_edit_text");
                if (s1 != null && !s1.isEmpty()) return true;

                // Kemungkinan 2 (Versi Lama/Standard)
                List<AccessibilityNodeInfo> s2 = root.findAccessibilityNodeInfosByViewId("com.samsung.android.messaging:id/message_edit_text");
                if (s2 != null && !s2.isEmpty()) return true;

                // Kemungkinan 3 (Custom Input)
                List<AccessibilityNodeInfo> s3 = root.findAccessibilityNodeInfosByViewId("com.samsung.android.messaging:id/mms_message_edit_text");
                if (s3 != null && !s3.isEmpty()) return true;

                return false;
        }

        if (targetId != null) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(targetId);
            return nodes != null && !nodes.isEmpty();
        }

        return false;
    }

    /**
     * Mencari input field dengan aturan GEOMETRI YANG LEBIH KETAT.
     */
    private boolean hasBottomMessageInput(AccessibilityNodeInfo node, int screenHeight, int depth) {
        if (node == null) return false;
        if (depth > 12) return false; // Depth limit tetap 12

        CharSequence clsCs = node.getClassName();
        String className = (clsCs != null) ? clsCs.toString() : "";

        // Skip container
        boolean isContainer = className.endsWith("Layout") ||
                className.endsWith("Group") ||
                className.endsWith("RecyclerView");

        if (!isContainer) {
            CharSequence textCs = node.getText();
            CharSequence descCs = node.getContentDescription();
            String text = (textCs != null) ? textCs.toString().toLowerCase(Locale.ROOT) : "";
            String desc = (descCs != null) ? descCs.toString().toLowerCase(Locale.ROOT) : "";

            // BLACKLIST KEYWORD: "Search", "Cari", "Telusuri"
            if (text.contains("search") || text.contains("cari") || text.contains("telusuri") ||
                    desc.contains("search") || desc.contains("cari") || desc.contains("telusuri")) {
                return false;
            }

            boolean looksLikeChatPlaceholder =
                    text.contains("ketik") || text.contains("kirim") ||
                            text.contains("tulis") || text.contains("message") ||
                            text.contains("say something") || // Instagram
                            desc.contains("message") || desc.contains("tulis") || desc.contains("input");

            boolean isEditText = node.isEditable() || className.endsWith("EditText");

            if (isEditText && looksLikeChatPlaceholder) {
                Rect r = new Rect();
                node.getBoundsInScreen(r);
                int centerY = (r.top + r.bottom) / 2;

                // 🔒 PERBAIKAN FATAL: Strict Threshold
                // Sebelumnya 0.55 (tengah layar). Itu salah karena tombol "Start Chat" ada di situ.
                // Kita ubah ke 0.80 (20% layar terbawah).
                // Kolom chat PASTI nempel di keyboard/bawah layar.
                if (centerY > screenHeight * 0.80f) {
                    return true;
                }
            }
        }

        // Reverse Loop (Tetap dipertahankan karena efisien)
        int childCount = node.getChildCount();
        int checkedCount = 0;
        for (int i = childCount - 1; i >= 0; i--) {
            if (checkedCount > 8) break;
            if (hasBottomMessageInput(node.getChild(i), screenHeight, depth + 1)) {
                return true;
            }
            checkedCount++;
        }

        return false;
    }

    // ================== EVENT HANDLING ==================

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

        // Cek dulu apakah user mematikan fitur lewat Toggle?
        if (!isDetectionEnabled()) {
            return;
        }

        if (event == null) return;

        CharSequence pkgCs = event.getPackageName();
        String pkgName  = (pkgCs != null) ? pkgCs.toString() : "";

        // Skip paket yang tidak mau discan
        if (isIgnoredPackage(pkgName) || isKeyboardPackage(pkgName)) {
            return;
        }

        // Hanya event tertentu
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                type != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            return;
        }

        // ❗ KUNCI: hanya scan kalau sudah di room chat/detail
        if (!isProbableChatRoomScreen(pkgName)) {
            Log.d(TAG, "Pkg " + pkgName + " belum di room chat/detail → skip scan");
            return;
        }

        CharSequence clsCs = event.getClassName();
        String className = (clsCs != null) ? clsCs.toString() : "";

        List<CharSequence> texts = event.getText();

        Log.d(TAG, "Event: " + AccessibilityEvent.eventTypeToString(type)
                + " | pkg=" + pkgName
                + " | cls=" + className
                + " | texts=" + texts);

        // 1) Teks langsung dari event
        if (texts != null) {
            for (CharSequence cs : texts) {
                if (cs != null) {
                    scanTextForUrl(cs.toString(), pkgName);
                }
            }
        }

        // 2) Telusuri tree view (bubble chat, dsb.)
        AccessibilityNodeInfo source = event.getSource();
        traverseNode(source, pkgName);
    }

    private void traverseNode(AccessibilityNodeInfo node, String sourcePackage) {
        if (node == null) return;

        CharSequence text = node.getText();
        if (text != null) {
            scanTextForUrl(text.toString(), sourcePackage);
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                traverseNode(child, sourcePackage);
            }
        }
    }

    private void scanTextForUrl(String text, String sourcePackage) {
        if (text == null || text.isEmpty()) return;

        if (!looksLikeUrlCandidate(text)) {
            return;
        }

        ensureModelUpToDate();

        Matcher matcher = URL_PATTERN.matcher(text);
        while (matcher.find()) {
            String raw = matcher.group(1);
            if (raw == null || raw.isEmpty()) continue;

            String trimmed = raw.trim();

            // Case-insensitive: cegah https://https://
            if (!trimmed.matches("(?i)^https?://.*")) {
                trimmed = "https://" + trimmed;
            }

            String url = trimmed;

            Log.d(TAG, "🔍 DITEMUKAN URL: raw=" + raw + " | normalized=" + url);
            classifyUrl(url, sourcePackage);
        }
    }

    // ================== KLASIFIKASI + OVERLAY ==================
    private void classifyUrl(String url, String sourcePackage) {
        if (urlClassifier == null) {
            Log.w(TAG, "UrlOnnxClassifier belum siap, skip URL: " + url);
            return;
        }

        // Debounce di main thread
        long nowDebounce = System.currentTimeMillis();
        Long lastSeen = urlRecentSeen.get(url);
        if (lastSeen != null && (nowDebounce - lastSeen) < URL_DEBOUNCE_MS) {
            Log.d(TAG, "Skip classify (debounce) untuk URL: " + url);
            return;
        }
        urlRecentSeen.put(url, nowDebounce);

        executor.execute(() -> {
            String originalUrl = url;
            String urlToCheck  = url;

            try {
                // Unshorten kalau perlu
                if (UrlResolver.isShortener(urlToCheck)) {
                    if (urlToCheck.startsWith("http://")) {
                        urlToCheck = "https://" + urlToCheck.substring("http://".length());
                    }

                    String resolved = UrlResolver.resolveFinalUrl(urlToCheck);
                    if (resolved != null && !resolved.isEmpty()) {
                        Log.d(TAG, "Short link terdeteksi. original=" + originalUrl
                                + " | resolved=" + resolved);
                        urlToCheck = resolved;
                    } else {
                        Log.d(TAG, "Short link gagal di-resolve, pakai original saja: " + originalUrl);
                        urlToCheck = originalUrl;
                    }
                }

                // Siapkan info algoritma yang dipakai
                DetectionModel modelUsed = lastModel;
                String algorithmUsed = null;

                if (modelUsed != null) {
                    switch (modelUsed) {
                        case XGBOOST: algorithmUsed = "XGBOOST"; break;
                        case DECISION_TREE: algorithmUsed = "DECISION_TREE"; break;
                        case KNN: algorithmUsed = "KNN"; break;
                        case GAUSSIAN_NB: algorithmUsed = "GAUSSIAN_NB"; break;
                        default: algorithmUsed = modelUsed.name(); break;
                    }
                }

                // =====================================================
                // 1) OTAK KEDUA DULU: RULE-BASED KEYWORD JUDOL / SCAM
                // =====================================================
                if (SensitiveKeywordHelper.containsSensitiveKeyword(urlToCheck)) {
                    Log.d(TAG, "Heuristic HIT: URL mengandung keyword sensitif: " + urlToCheck);

                    String host = UrlUtils.getHost(urlToCheck);
                    boolean isInstDomain   = isTrustedInstitutionDomain(host);
                    boolean isGoogleDomain = isGoogleProductDomain(host);

                    // Kalau user whitelist domain-nya, tetap hormati whitelist
                    if (UserWhitelist.isTrusted(this, host)) {
                        Log.d(TAG, "Heuristic hit tapi host di user-whitelist, skip overlay: " + urlToCheck);
                        return;
                    }

                    long nowInside = System.currentTimeMillis();
                    String urlKey = buildUrlKey(urlToCheck);

                    // Same-screen cooldown
                    if (urlKey.equals(lastShownUrlKey)
                            && sourcePackage.equals(lastShownPackage)
                            && (nowInside - lastShownTime) < SAME_SCREEN_COOLDOWN_MS) {
                        Log.d(TAG, "Skip warning (same-screen cooldown, heuristic) untuk: " + urlKey);
                        return;
                    }
                    lastShownUrlKey = urlKey;
                    lastShownPackage = sourcePackage;
                    lastShownTime = nowInside;

                    // Cooldown global per URL
                    Long lastTime = urlWarningHistory.get(urlKey);
                    if (lastTime != null && (nowInside - lastTime) < URL_COOLDOWN_MS) {
                        Log.d(TAG, "Skip warning (URL cooldown, heuristic) untuk: " + urlKey);
                        return;
                    }
                    urlWarningHistory.put(urlKey, nowInside);

                    // Simpan ke DB sebagai HIGH full (1.0)
                    if (dbHelper != null) {
                        try {
                            dbHelper.insertScanHistory(
                                    nowInside,
                                    sourcePackage,
                                    originalUrl,
                                    urlToCheck,
                                    host,
                                    1.0f,          // score heuristik → 1.0
                                    "HIGH",        // label heuristik
                                    algorithmUsed
                            );
                        } catch (Exception e) {
                            Log.e(TAG, "Gagal insert riwayat scan (heuristic) ke DB", e);
                        }
                    }

                    // Tampilkan overlay HIGH dari heuristik
                    if (warningOverlay != null && warningOverlay.canDrawOverlays()) {
                        final String finalOriginalUrl = originalUrl;
                        final String finalCheckedUrl  = urlToCheck;
                        final float  prob             = 1.0f;
                        final String label            = "HIGH";
                        final String pkgForOverlay    = sourcePackage;
                        final String algoForOverlay   = algorithmUsed;
                        final boolean finalIsInst   = isInstDomain;
                        final boolean finalIsGoogle = isGoogleDomain;
                        final long   shownAt        = nowInside;

                        mainHandler.post(() ->
                                warningOverlay.show(
                                        pkgForOverlay,
                                        finalOriginalUrl,
                                        finalCheckedUrl,
                                        prob,
                                        label,
                                        shownAt,
                                        host,
                                        finalIsInst,
                                        finalIsGoogle,
                                        algoForOverlay
                                )
                        );
                    }
                    return;
                }

                // ==========================================
                // 2) KALAU LOLOS HEURISTIK → LANJUT KE ML
                // ==========================================

                UrlOnnxClassifier.Result result = urlResultCache.get(urlToCheck);
                if (result == null) {
                    result = urlClassifier.classifyUrl(urlToCheck);
                    urlResultCache.put(urlToCheck, result);
                } else {
                    Log.d(TAG, "Pakai hasil cache untuk URL: " + urlToCheck);
                }

                if (DEBUG_FORCE_TEST_HIGH) {
                    String lower = urlToCheck.toLowerCase(Locale.ROOT);
                    if (lower.contains("test-high")) {
                        result = new UrlOnnxClassifier.Result(
                                0.99f,
                                "HIGH",
                                result.threshold
                        );
                        Log.d(TAG, "TEST-OVERRIDE: URL dipaksa HIGH: " + urlToCheck);
                    }
                }

                if (result == null) {
                    Log.w(TAG, "Result klasifikasi null, skip overlay.");
                    return;
                }

                String host = UrlUtils.getHost(urlToCheck);
                boolean isInstDomain   = isTrustedInstitutionDomain(host);
                boolean isGoogleDomain = isGoogleProductDomain(host);
                boolean isSafeSimple   = isSafeSimpleComLikeDomain(host);

                // User whitelist (dipilih user sendiri)
                if (UserWhitelist.isTrusted(this, host)) {
                    Log.d(TAG, "URL di domain user-whitelist, skip overlay: " + urlToCheck);
                    return;
                }

                // cek domain sederhana .com / .co.id / .ac.id dianggap aman
                // dan isGoogleDomain juga dianggap aman (default logic)
                if (isSafeSimple || isGoogleDomain) {
                    Log.d(TAG, "Host dianggap aman (simple domain atau Google Product), skip overlay: " + host);
                    return;
                }

                // 🔥 PERUBAHAN UTAMA: LOGIC SLIDER vs HARD OVERRIDE 🔥

                // 1. Ambil nilai slider dari user (Default 0.5 jika belum diset)
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                float userSetThreshold = prefs.getFloat(PREF_RISK_THRESHOLD, 0.5f);

                // 2. Set threshold dasar sesuai slider user
                float overlayThreshold = userSetThreshold;

                // 3. Logic PENGECUALIAN (Override)
                // Jika domain sekolah/kampus, kita "PAKSA" threshold jadi 0.95
                // (agar logic lama tetap jalan dan tidak terpengaruh slider user)
                if (isInstDomain) {
                    overlayThreshold = 0.95f;
                    Log.d(TAG, "Override Threshold Instansi: 0.95");
                }
                // Jika produk Google, paksa 0.90
                else if (isGoogleDomain) {
                    overlayThreshold = 0.90f;
                    Log.d(TAG, "Override Threshold Google: 0.90");
                }

                // Cek skor prediksi vs threshold yang sudah ditentukan
                if (result.probPhishing < overlayThreshold) {
                    Log.d(TAG, "Skor (" + result.probPhishing + ") di bawah Threshold (" +
                            overlayThreshold + ") untuk host=" + host + ", skip overlay.");
                    return;
                }

                // Hanya tampilkan & simpan kalau label HIGH
                if (!"HIGH".equalsIgnoreCase(result.riskLabel)) {
                    Log.d(TAG, "Label bukan HIGH (label=" + result.riskLabel +
                            "), skip DB & overlay untuk URL: " + urlToCheck);
                    return;
                }

                // Anti-spam (same screen cooldown)
                long nowInside = System.currentTimeMillis();
                String urlKey = buildUrlKey(urlToCheck);

                if (urlKey.equals(lastShownUrlKey)
                        && sourcePackage.equals(lastShownPackage)
                        && (nowInside - lastShownTime) < SAME_SCREEN_COOLDOWN_MS) {
                    Log.d(TAG, "Skip warning (same-screen cooldown) untuk: " + urlKey);
                    return;
                }
                lastShownUrlKey = urlKey;
                lastShownPackage = sourcePackage;
                lastShownTime = nowInside;

                // Cooldown global per URL
                Long lastTime = urlWarningHistory.get(urlKey);
                if (lastTime != null && (nowInside - lastTime) < URL_COOLDOWN_MS) {
                    Log.d(TAG, "Skip warning (URL cooldown) untuk: " + urlKey);
                    return;
                }
                urlWarningHistory.put(urlKey, nowInside);

                // ====== SIMPAN KE DATABASE RIWAYAT (HASIL ML) ======
                if (dbHelper != null) {
                    try {
                        dbHelper.insertScanHistory(
                                nowInside,
                                sourcePackage,
                                originalUrl,
                                urlToCheck,
                                host,
                                result.probPhishing,
                                result.riskLabel,
                                algorithmUsed
                        );
                    } catch (Exception e) {
                        Log.e(TAG, "Gagal insert riwayat scan ke DB", e);
                    }
                }

                // ====== TAMPILKAN OVERLAY (HANYA HIGH) ======
                if (warningOverlay != null && warningOverlay.canDrawOverlays()) {
                    final String finalOriginalUrl = originalUrl;
                    final String finalCheckedUrl  = urlToCheck;
                    final float  prob             = result.probPhishing;
                    final String label            = result.riskLabel;
                    final String pkgForOverlay    = sourcePackage;
                    final String algoForOverlay   = algorithmUsed;
                    final boolean finalIsInst   = isInstDomain;
                    final boolean finalIsGoogle = isGoogleDomain;
                    final long   shownAt        = nowInside;

                    mainHandler.post(() ->
                            warningOverlay.show(
                                    pkgForOverlay,
                                    finalOriginalUrl,
                                    finalCheckedUrl,
                                    prob,
                                    label,
                                    shownAt,
                                    host,
                                    finalIsInst,
                                    finalIsGoogle,
                                    algoForOverlay
                            )
                    );
                } else {
                    Log.w(TAG, "Overlay belum diizinkan / null. Tidak bisa tampilkan warning.");
                }

            } catch (Exception e) {
                Log.e(TAG, "Gagal klasifikasi URL (background): " + url, e);
            }
        });
    }
}