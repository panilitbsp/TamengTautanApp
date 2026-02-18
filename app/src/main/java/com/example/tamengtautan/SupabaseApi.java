package com.example.tamengtautan;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface SupabaseApi {

    // insert 1 device, tapi API-nya minta ARRAY
    @Headers({
            "Content-Type: application/json",
            "Prefer: return=representation"
    })
    @POST("rest/v1/devices")
    Call<List<SupabaseDeviceResponse>> insertDevice(
            @Body List<SupabaseDevice> devices
    );

    // insert banyak riwayat sekaligus
    @Headers({
            "Content-Type: application/json",
            "Prefer: return=representation"
    })
    @POST("rest/v1/scan_history")
    Call<List<SupabaseScanHistoryResponse>> insertScanHistory(
            @Body List<SupabaseScanHistory> history
    );
}
