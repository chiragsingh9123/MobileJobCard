package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.JsonObject;
import com.revenueaccount.app.R;
import com.revenueaccount.app.api.ApiClient;
import java.util.HashMap;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Forgot Password - two steps in one screen:
 * 1) Enter the registered mobile number, request a reset code (uses the same
 *    OTP infrastructure as login/register, purpose=RESET_PASSWORD).
 * 2) Enter the code plus a new password to complete the reset. */
public class ForgotPasswordActivity extends AppCompatActivity {

    public static final int RESEND_SECONDS = 45;

    private View stepMobile, stepReset;
    private EditText etMobile, etOtp, etNewPassword, etConfirmPassword;
    private Button btnSendOtp, btnResetPassword;
    private TextView tvResendOtp, tvOtpSentTo;
    private CountDownTimer timer;
    private String mobile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        stepMobile = findViewById(R.id.stepMobile);
        stepReset = findViewById(R.id.stepReset);
        etMobile = findViewById(R.id.etMobile);
        etOtp = findViewById(R.id.etOtp);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnSendOtp = findViewById(R.id.btnSendOtp);
        btnResetPassword = findViewById(R.id.btnResetPassword);
        tvResendOtp = findViewById(R.id.tvResendOtp);
        tvOtpSentTo = findViewById(R.id.tvOtpSentTo);

        btnSendOtp.setOnClickListener(v -> sendOtp(false));
        tvResendOtp.setOnClickListener(v -> sendOtp(true));
        btnResetPassword.setOnClickListener(v -> resetPassword());
    }

    private void sendOtp(boolean isResend) {
        if (!isResend) {
            mobile = etMobile.getText().toString().trim();
            if (mobile.length() != 10) {
                etMobile.setError("Enter a valid 10-digit mobile number");
                return;
            }
        }
        Map<String, String> body = new HashMap<>();
        body.put("mobile", mobile);
        body.put("purpose", "RESET_PASSWORD");

        Button trigger = isResend ? null : btnSendOtp;
        if (trigger != null) trigger.setEnabled(false);
        if (isResend) tvResendOtp.setEnabled(false);

        ApiClient.get(this).sendOtp(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                if (trigger != null) trigger.setEnabled(true);
                if (res.isSuccessful()) {
                    if (!isResend) {
                        stepMobile.setVisibility(View.GONE);
                        stepReset.setVisibility(View.VISIBLE);
                        tvOtpSentTo.setText("A 6-digit code has been sent to " + mobile);
                    } else {
                        AppToast.success(ForgotPasswordActivity.this, "A new code has been sent");
                    }
                    startCooldown();
                } else {
                    tvResendOtp.setEnabled(true);
                    AppToast.error(ForgotPasswordActivity.this, extractError(res, "Could not send the code"));
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                if (trigger != null) trigger.setEnabled(true);
                tvResendOtp.setEnabled(true);
                AppToast.error(ForgotPasswordActivity.this, "Network error");
            }
        });
    }

    private void startCooldown() {
        tvResendOtp.setEnabled(false);
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(RESEND_SECONDS * 1000L, 1000) {
            @Override public void onTick(long ms) {
                tvResendOtp.setText("Resend Code (" + (ms / 1000) + "s)");
            }
            @Override public void onFinish() {
                tvResendOtp.setText("Resend Code");
                tvResendOtp.setEnabled(true);
            }
        };
        timer.start();
    }

    private void resetPassword() {
        String otp = etOtp.getText().toString().trim();
        String newPassword = etNewPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();

        if (otp.length() != 6) { etOtp.setError("Enter the 6-digit code"); return; }
        if (newPassword.length() < 4) { etNewPassword.setError("At least 4 characters"); return; }
        if (!newPassword.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            return;
        }

        btnResetPassword.setEnabled(false);
        Map<String, String> body = new HashMap<>();
        body.put("mobile", mobile);
        body.put("otp", otp);
        body.put("new_password", newPassword);
        ApiClient.get(this).resetPassword(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                btnResetPassword.setEnabled(true);
                if (res.isSuccessful()) {
                    AppToast.success(ForgotPasswordActivity.this, "Password reset successfully - please log in");
                    finish();
                } else {
                    AppToast.error(ForgotPasswordActivity.this, extractError(res, "Could not reset the password"));
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                btnResetPassword.setEnabled(true);
                AppToast.error(ForgotPasswordActivity.this, "Network error");
            }
        });
    }

    private String extractError(Response<JsonObject> res, String fallback) {
        try {
            if (res.errorBody() != null) {
                JsonObject err = com.google.gson.JsonParser.parseString(res.errorBody().string()).getAsJsonObject();
                if (err.has("detail")) return err.get("detail").getAsString();
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
    }
}
