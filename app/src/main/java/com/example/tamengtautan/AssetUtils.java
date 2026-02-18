package com.example.tamengtautan;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class AssetUtils {

    public static String readAssetFile(Context context, String fileName) throws IOException {
        InputStream is = context.getAssets().open(fileName);
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;

        while ((line = br.readLine()) != null) {
            sb.append(line);
        }

        br.close();
        is.close();
        return sb.toString();
    }
}
