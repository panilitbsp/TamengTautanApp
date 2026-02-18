package com.example.tamengtautan;

import android.net.Uri;
import java.net.IDN;

public class FeatureExtractor {

    public static float[] extractFeatures(String url, FeatureSpec spec) {

        int n = spec.feature_count;      // misal 87
        float[] features = new float[n];

        String[] names = spec.feature_names;

        for (int i = 0; i < n; i++) {
            String name = names[i];

            switch (name) {
                case "length_url":
                    features[i] = url.length();
                    break;

                case "length_hostname":
                    features[i] = getHostLength(url);
                    break;

                case "nb_dots":
                    features[i] = countChar(url, '.');
                    break;

                case "nb_hyphens":
                    features[i] = countChar(url, '-');
                    break;

                // TODO: tambahin fitur lain sesuai feature_spec.json
                // case "nb_at":
                // case "nb_qm":
                // dst...

                default:
                    features[i] = 0f;
                    break;
            }
        }

        return features;
    }

    private static int getHostLength(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host == null) return 0;
            host = IDN.toASCII(host);
            return host.length();
        } catch (Exception e) {
            return 0;
        }
    }

    private static int countChar(String s, char c) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) count++;
        }
        return count;
    }
}