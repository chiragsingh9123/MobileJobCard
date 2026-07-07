package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.content.Intent;
import android.os.Bundle;
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

/** Screen 2: Login — mobile+password check karke OTP verification par bhejta hai */
public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        EditText etMobile = findViewById(R.id.etMobile);
        EditText etPassword = findViewById(R.id.etPassword);
        Button btnLogin = findViewById(R.id.btnLogin);
        TextView tvRegister = findViewById(R.id.tvRegister);

        tvRegister.setOnClickListener(v ->
        startActivity(new Intent(this, RegisterActivity.class)));

        btnLogin.setOnClickListener(v -> {
            String mobile = etMobile.getText().toString().trim();
            String password = etPassword.getText().toString();
            if (mobile.length() != 10) { etMobile.setError("a 10-digit mobile number daalein"); return; }
            if (password.isEmpty()) { etPassword.setError("Password daalein"); return; }

            btnLogin.setEnabled(false);
            // Pehle OTP bhejo (backend verify karega ki mobile registered hai)
            Map<String, String> body = new HashMap<>();
            body.put("mobile", mobile);
            body.put("purpose", "LOGIN");
            ApiClient.get(this).sendOtp(body).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                    btnLogin.setEnabled(true);
                    if (res.isSuccessful()) {
                        Intent i = new Intent(LoginActivity.this, OtpVerifyActivity.class);
                        i.putExtra("purpose", "LOGIN");
                        i.putExtra("mobile", mobile);
                        i.putExtra("password", password);
                        startActivity(i);
                    } else {
                        AppToast.show(LoginActivity.this, "This mobile number is not registered, or there was a server error");
                    }
                }
                @Override
                public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                    btnLogin.setEnabled(true);
                    AppToast.show(LoginActivity.this, "Could not connect to the server. please check your internet connection or server.");
                }
            });
        });
    }
}
