package com.revenueaccount.app.api;

import android.content.Context;
import android.content.Intent;
import com.revenueaccount.app.activities.SubscriptionActivity;
import com.revenueaccount.app.utils.SessionManager;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    // Apne server ka IP daalein. Emulator ke liye 10.0.2.2 = aapka PC ka localhost
    public static final String BASE_URL = "https://api.mobilejobcard.com/";
    private static Retrofit retrofit;

    // Guards against firing the paywall redirect multiple times if several
    // API calls come back with 402 in quick succession (common right after
    // a subscription expires, since the app may have a few requests in
    // flight at once) - reset once the redirect actually happens.
    private static final AtomicBoolean redirectingToPaywall = new AtomicBoolean(false);

    public static ApiService get(Context context) {
        if (retrofit == null) {
            Context appContext = context.getApplicationContext();
            SessionManager session = new SessionManager(appContext);
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(chain -> {
                        Request original = chain.request();
                        String token = session.getAccessToken();
                        if (token != null && !original.url().encodedPath().contains("/api/auth/login")
                                && !original.url().encodedPath().contains("/api/auth/register")) {
                            original = original.newBuilder()
                                    .header("Authorization", "Bearer " + token).build();
                        }
                        Response response = chain.proceed(original);
                        if (response.code() == 402 && redirectingToPaywall.compareAndSet(false, true)) {
                            Intent intent = new Intent(appContext, SubscriptionActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            appContext.startActivity(intent);
                            redirectingToPaywall.set(false);
                        }
                        return response;
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