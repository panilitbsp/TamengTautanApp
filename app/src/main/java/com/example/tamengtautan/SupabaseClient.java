// SupabaseClient.java
package com.example.tamengtautan;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class SupabaseClient {

    private static final String SUPABASE_URL = "https://atpqfxwraqzbzgfaewsp.supabase.co";
    private static final String SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImF0cHFmeHdyYXF6YnpnZmFld3NwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjY1OTIyNTIsImV4cCI6MjA4MjE2ODI1Mn0.7-QmYuD0Jm4cDyLB5ClNXHXCOxJNNMwxbAK0avIdHN4";

    private static SupabaseApi api;

    public static SupabaseApi getApi() {
        if (api == null) {
            HttpLoggingInterceptor log = new HttpLoggingInterceptor();
            log.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(log)
                    .addInterceptor(new Interceptor() {
                        @Override
                        public Response intercept(Chain chain) throws IOException {
                            Request original = chain.request();
                            Request req = original.newBuilder()
                                    .header("apikey", SUPABASE_ANON_KEY)
                                    .header("Authorization", "Bearer " + SUPABASE_ANON_KEY)
                                    .build();
                            return chain.proceed(req);
                        }
                    })
                    .build();

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(SUPABASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            api = retrofit.create(SupabaseApi.class);
        }
        return api;
    }
}