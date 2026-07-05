package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
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

/** Screen 3: Register — form bharo, OTP verification par jao, phir account bane */
public class RegisterActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        EditText etShop = findViewById(R.id.etShopName);
        EditText etOwner = findViewById(R.id.etOwnerName);
        EditText etMobile = findViewById(R.id.etMobile);
        EditText etEmail = findViewById(R.id.etEmail);
        EditText etPassword = findViewById(R.id.etPassword);
        EditText etConfirm = findViewById(R.id.etConfirmPassword);
        Button btnRegister = findViewById(R.id.btnRegister);

        btnRegister.setOnClickListener(v -> {
            String shop = etShop.getText().toString().trim();
            String owner = etOwner.getText().toString().trim();
            String mobile = etMobile.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString();

            if (shop.isEmpty()) { etShop.setError("Shop ka naam daalein"); return; }
            if (owner.isEmpty()) { etOwner.setError("Apna naam daalein"); return; }
            if (mobile.length() != 10) { etMobile.setError("10 digit mobile number"); return; }
            if (password.length() < 6) { etPassword.setError("Kam se kam 6 characters"); return; }
            if (!password.equals(etConfirm.getText().toString())) {
                etConfirm.setError("Password match nahi kar raha"); return;
            }

            btnRegister.setEnabled(false);
            // Pehle OTP bhejo (backend confirm karega mobile already registered to nahi)
            Map<String, String> body = new HashMap<>();
            body.put("mobile", mobile);
            body.put("purpose", "REGISTER");
            ApiClient.get(this).sendOtp(body).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                    btnRegister.setEnabled(true);
                    if (res.isSuccessful()) {
                        Intent i = new Intent(RegisterActivity.this, OtpVerifyActivity.class);
                        i.putExtra("purpose", "REGISTER");
                        i.putExtra("mobile", mobile);
                        i.putExtra("password", password);
                        i.putExtra("shop_name", shop);
                        i.putExtra("first_name", owner);
                        i.putExtra("email", email);
                        startActivity(i);
                    } else {
                        AppToast.show(RegisterActivity.this, "Yeh mobile number pehle se registered hai, ya server error hai");
                    }
                }
                @Override
                public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                    btnRegister.setEnabled(true);
                    AppToast.show(RegisterActivity.this, "Network error: " + t.getMessage());
                }
            });
        });
    }
}
