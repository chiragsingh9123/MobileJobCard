package com.revenueaccount.app.api;

import android.content.Context;
import com.revenueaccount.app.utils.SessionManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    // Apne server ka IP daalein. Emulator ke liye 10.0.2.2 = aapka PC ka localhost
    public static final String BASE_URL = "http://187.127.186.197:8000/";
    private static Retrofit retrofit;

    public static ApiService get(Context context) {
        if (retrofit == null) {
            SessionManager session = new SessionManager(context.getApplicationContext());
            OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(chain -> {
                Request original = chain.request();
                String token = session.getAccessToken();
                if (token != null && !original.url().encodedPath().contains("/api/auth/login")
                && !original.url().encodedPath().contains("/api/auth/register")) {
                    original = original.newBuilder()
                    .header("Authorization", "Bearer " + token).build();
                }
                return chain.proceed(original);
            }).build();
            retrofit = new Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build();
        }
        return retrofit.create(ApiService.class);
    }
}
