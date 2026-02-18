package com.example.tamengtautan;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class SensitiveKeywordHelper {

    /**
     * Fokus:
     * 1) JUDI ONLINE / SLOT ONLINE
     * 2) SCAM / PHISHING INSTANSI (bank, bantuan, dll.)
     */

    // 🔥 Kata yang sangat umum dipakai (slot/judol)
    private static final String[] CORE_KEYWORDS = new String[]{
            "slot",
            "slot online",
            "judi online",
            "casino",
            "bet",
            "betting",
            "rtp",
            "rtp tinggi",
            "rtp live",
            "rtp slot",
            "jackpot",
            "jackpot besar",
            "jackpot maxwin",
            "withdraw",
            "wd cepat",
            "sensational",
            "big win",
            "mega win",
            "ultimate win",
            "free spin",
            "freespin",
            "spin",
            "spinwheel",
            "spin wheel",
            "spin wheels",
            "spinwheels",
            "maxwheel",
            "max wheel",
            "spinwheel jackpot",
            "gacha slot"
    };

    // 🔥 Istilah khas dunia slot
    private static final String[] SLOT_TERMS = new String[]{
            "gacor",
            "gacor hari ini",
            "polosan",
            "buyspin",
            "buy spin",
            "scatter",
            "double chance",
            "turbo spin",
            "auto spin",
            "modal receh",
            "deposit pulsa",
            "tanpa potongan",
            "jp sensasional",
            "anti rungkat",
            "bocoran slot",
            "bocoran gacor",
            "pattern hari ini",
            "room gacor",
            "rtp naik",
            "rtp 98",
            "rtp 97",
            "rtp tinggi"
    };

    // 🔥 Brand / angka ciri khas judol
    private static final String[] BRAND_NUMBERS = new String[]{
            "88",
            "777",
            "999",
            "188",
            "388",
            "588",
            "818",
            "868"
    };

    // 🔥 Nama brand / situs yang sering muncul
    private static final String[] BRAND_KEYWORDS = new String[]{
            "texas88",
            "hoki88",
            "hoki 88",
            "hoki88bos",
            "slot88",
            "slot 88",
            "ayo88",
            "oyo777",
            "king88",
            "mega88",
            "maxwin88",
            "indo slot",
            "win88",
            "jackpot88",
            "gold88",
            "bo slot"
    };

    // 🔥 Nama game slot populer
    private static final String[] GAME_NAMES = new String[]{
            "mahjong",
            "mahjong ways",
            "mahjong ways 2",
            "starlight princess",
            "gates of olympus",
            "sweet bonanza",
            "wild west gold",
            "sugar rush",
            "power of thor",
            "dog house",
            "bonanza",
            "zeus slot"
    };

    // 🔥 Game judi khas Indonesia
    private static final String[] LOCAL_GAMBLING = new String[]{
            "domino",
            "higgs domino",
            "domino rp",
            "chip domino",
            "top up chip",
            "jual chip",
            "qiu qiu",
            "poker",
            "bandar",
            "bandarq",
            "slot domino"
    };

    // 🔥 Kata-kata SCAM / PHISHING yang nyaru sebagai bank / bantuan / hadiah
    private static final String[] SCAM_KEYWORDS = new String[]{
            // bank / bonus / reward
            "klaim bonus bca",
            "claim bonus bca",
            "bonus bca",
            "klaim bonus",
            "claim bonus",
            "klaim hadiah",
            "hadiah undian",
            "hadiah langsung",
            "program loyalti",
            "program loyalty",
            "reward nasabah",
            "nasabah setia",
            "saldo hadiah",

            // bantuan / subsidi / pemerintah
            "subsidi cair",
            "bantuan sosial cair",
            "bansos cair",
            "cek penerima bantuan",
            "cek penerima bansos",
            "penerima bantuan",
            "penerima subsidi",
            "bantuan pemerintah",
            "bantuan langsung tunai",
            "blt cair",
            "cair cepat",
            "pencairan dana",

            // pinjaman / kredit instan
            "pinjaman instan",
            "pinjaman online cepat",
            "tanpa jaminan",
            "tanpa agunan",
            "bunga rendah",
            "cair dalam hitungan menit",
            "limit kredit tambahan",
            "ajukan sekarang",

            // akun / keamanan palsu
            "verifikasi akun",
            "akun diblokir",
            "akun terblokir",
            "akun anda diblokir",
            "akun anda terblokir",
            "login ulang disini",
            "login ulang di sini",
            "login ulang di link berikut",
            "konfirmasi data",
            "update data anda",
            "perbaharui data",
            "pembaruan data",
            "blokir sementara",
            "keamanan akun",

            // hadiah / gimmick marketing penipuan
            "mystery box",
            "mysterybox",
            "spin hadiah",
            "spin reward",
            "spin roda hadiah",
            "hadiah kejutan",
            "hadiah spesial",
            "undian berhadiah",
            "voucher gratis",
            "klaim hadiah anda"
    };

    private static final Set<String> KEYWORD_SET = new HashSet<>();

    static {
        KEYWORD_SET.addAll(Arrays.asList(CORE_KEYWORDS));
        KEYWORD_SET.addAll(Arrays.asList(SLOT_TERMS));
        KEYWORD_SET.addAll(Arrays.asList(BRAND_KEYWORDS));
        KEYWORD_SET.addAll(Arrays.asList(GAME_NAMES));
        KEYWORD_SET.addAll(Arrays.asList(LOCAL_GAMBLING));
        KEYWORD_SET.addAll(Arrays.asList(SCAM_KEYWORDS));   // ⬅️ ini yang baru
    }

    private SensitiveKeywordHelper() {}

    /**
     * Mengecek apakah teks / URL mengandung indikasi kuat judi online / scam.
     * Dipakai oleh "otak kedua" sebelum ML.
     */
    public static boolean containsSensitiveKeyword(String text) {
        if (text == null) return false;

        // lowercase dulu
        String lower = text.toLowerCase(Locale.ROOT);

        // 🔧 Normalisasi: samakan separator jadi spasi
        String normalized = lower
                .replace("-", " ")
                .replace("_", " ")
                .replace("%20", " ")
                .replace("+", " ")
                .replace("/", " ");

        // rapikan spasi ganda jadi satu
        normalized = normalized.replaceAll("\\s+", " ");

        // 🔎 Cek semua keyword (judol + scam)
        for (String kw : KEYWORD_SET) {
            String kwLower = kw.toLowerCase(Locale.ROOT);
            if (normalized.contains(kwLower)) {
                return true;
            }
        }

        // 🔎 Cek pola angka khas judol (88, 777, dll.) + konteks slot/judi
        for (String num : BRAND_NUMBERS) {
            if (lower.contains(num)) {
                if (normalized.contains("slot")
                        || normalized.contains("gacor")
                        || normalized.contains("judol")
                        || normalized.contains("maxwin")
                        || normalized.contains("casino")
                        || normalized.contains("rtp")
                        || normalized.contains("spin")) {
                    return true;
                }
            }
        }

        return false;
    }
}