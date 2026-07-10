package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.JsonObject;
import com.revenueaccount.app.R;
import com.revenueaccount.app.api.ApiClient;
import com.revenueaccount.app.utils.SessionManager;
import java.util.HashMap;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * OTP Verification — Login aur Register dono isi screen se guzarte hain.
 * Purpose ke hisaab se ya to login() ya register() final call karta hai.
 * Filhaal OTP Telegram bot par jaata hai (SMS gateway baad me lagega).
 */
public class OtpVerifyActivity extends AppCompatActivity {

    private static final String TAG = "OtpVerifyActivity";
    public static final int RESEND_SECONDS = 45;

    private String purpose, mobile, password, shopName, ownerName, email;
    private EditText etOtp;
    private Button btnVerify;
    private TextView tvResend;
    private CountDownTimer timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp_verify);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        purpose = getIntent().getStringExtra("purpose");
        mobile = getIntent().getStringExtra("mobile");
        password = getIntent().getStringExtra("password");
        shopName = getIntent().getStringExtra("shop_name");
        ownerName = getIntent().getStringExtra("first_name");
        email = getIntent().getStringExtra("email");

        ((TextView) findViewById(R.id.tvSubtitle)).setText(
                "We have sent a 6-digit OTP to " + mobile + ". Enter it below.");

        etOtp = findViewById(R.id.etOtp);
        btnVerify = findViewById(R.id.btnVerify);
        tvResend = findViewById(R.id.tvResend);

        btnVerify.setOnClickListener(v -> verify());
        tvResend.setOnClickListener(v -> resend());
        startCooldown();
    }

    private void startCooldown() {
        tvResend.setEnabled(false);
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(RESEND_SECONDS * 1000L, 1000) {
            @Override public void onTick(long ms) {
                tvResend.setText("Resend (" + (ms / 1000) + "s)");
            }
            @Override public void onFinish() {
                tvResend.setText("Resend");
                tvResend.setEnabled(true);
            }
        };
        timer.start();
    }

    private void resend() {
        Map<String, String> body = new HashMap<>();
        body.put("mobile", mobile);
        body.put("purpose", purpose);
        tvResend.setEnabled(false);
        ApiClient.get(this).sendOtp(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                if (res.isSuccessful()) {
                    AppToast.show(OtpVerifyActivity.this, "A new OTP has been sent");
                    startCooldown();
                } else {
                    AppToast.show(OtpVerifyActivity.this, "The OTP could not be resent");
                    tvResend.setEnabled(true);
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                AppToast.show(OtpVerifyActivity.this, "Network error");
                tvResend.setEnabled(true);
            }
        });
    }

    private void verify() {
        String otp = etOtp.getText().toString().trim();
        if (otp.length() != 6) { etOtp.setError("Enter the 6-digit OTP"); return; }
        btnVerify.setEnabled(false);

        if ("LOGIN".equals(purpose)) {
            Map<String, String> body = new HashMap<>();
            body.put("mobile", mobile);
            body.put("password", password);
            body.put("otp", otp);
            ApiClient.get(this).login(body).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                    btnVerify.setEnabled(true);
                    if (res.isSuccessful() && res.body() != null) {
                        onLoginSuccess(res.body());
                    } else {
                        showError(res, "Incorrect OTP");
                    }
                }
                @Override
                public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                    btnVerify.setEnabled(true);
                    AppToast.show(OtpVerifyActivity.this, "Network error");
                }
            });
        } else {
            Map<String, String> body = new HashMap<>();
            body.put("shop_name", shopName);
            body.put("first_name", ownerName);
            body.put("mobile", mobile);
            body.put("email", email == null ? "" : email);
            body.put("password", password);
            body.put("otp", otp);
            ApiClient.get(this).register(body).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                    btnVerify.setEnabled(true);
                    if (res.isSuccessful() && res.body() != null) {
                        onRegisterSuccess(res.body());
                    } else {
                        showError(res, "Incorrect OTP, or registration failed");
                    }
                }
                @Override
                public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                    btnVerify.setEnabled(true);
                    AppToast.show(OtpVerifyActivity.this, "Network error");
                }
            });
        }
    }

    private void showError(Response<JsonObject> res, String fallback) {
        String msg = fallback;
        try {
            if (res.errorBody() != null) {
                JsonObject err = com.google.gson.JsonParser.parseString(
                        res.errorBody().string()).getAsJsonObject();
                if (err.has("detail")) msg = err.get("detail").getAsString();
            }
        } catch (Exception ignored) {}
        AppToast.show(this, msg);
    }

    private void onLoginSuccess(JsonObject data) {
        try {
            JsonObject tokens = data.getAsJsonObject("tokens");
            JsonObject user = data.getAsJsonObject("user");
            SessionManager session = new SessionManager(this);
            session.saveTokens(tokens.get("access").getAsString(), tokens.get("refresh").getAsString());
            String shop = user.has("shop") && !user.get("shop").isJsonNull()
                    ? user.getAsJsonObject("shop").get("name").getAsString() : "";
            session.saveUser(user.get("first_name").getAsString(), user.get("mobile").getAsString(), shop);
            if (user.has("role") && !user.get("role").isJsonNull()) session.saveRole(user.get("role").getAsString());
            com.revenueaccount.app.messaging.MyFirebaseMessagingService.syncTokenWithServer(this);
            startActivity(new Intent(this, DashboardActivity.class));
            finishAffinity();
        } catch (Exception e) {
            Log.e(TAG, "Login success parse error", e);
            AppToast.show(this, "Something went wrong");
        }
    }

    private void onRegisterSuccess(JsonObject data) {
        try {
            JsonObject tokens = data.getAsJsonObject("tokens");
            SessionManager session = new SessionManager(this);
            session.saveTokens(tokens.get("access").getAsString(), tokens.get("refresh").getAsString());
            session.saveUser(ownerName, mobile, shopName);
            session.saveRole("OWNER");
            com.revenueaccount.app.messaging.MyFirebaseMessagingService.syncTokenWithServer(this);
            AppToast.show(this, "Account created! Your 7-day FREE trial has started");
            startActivity(new Intent(this, DashboardActivity.class));
            finishAffinity();
        } catch (Exception e) {
            Log.e(TAG, "Register success parse error", e);
            AppToast.show(this, "Something went wrong");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
    }
}