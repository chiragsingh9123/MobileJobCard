package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
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

/** Shop Profile — dukaan ki basic details dekhein/edit karein (sirf OWNER edit kar sakta hai) */
public class ShopProfileActivity extends AppCompatActivity {

    private static final String TAG = "ShopProfileActivity";
    private EditText etName, etAddress, etCity, etGst;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shop_profile);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        etName = findViewById(R.id.etName);
        etAddress = findViewById(R.id.etAddress);
        etCity = findViewById(R.id.etCity);
        etGst = findViewById(R.id.etGst);
        Button btnSave = findViewById(R.id.btnSave);

        boolean isOwner = new SessionManager(this).isOwner();
        btnSave.setVisibility(isOwner ? android.view.View.VISIBLE : android.view.View.GONE);
        if (!isOwner) {
            for (EditText et : new EditText[]{etName, etAddress, etCity, etGst}) et.setEnabled(false);
        }

        load();
        btnSave.setOnClickListener(v -> save());
    }

    private void load() {
        ApiClient.get(this).shopProfile().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                if (!res.isSuccessful() || res.body() == null) return;
                try {
                    JsonObject d = res.body();
                    etName.setText(str(d, "name"));
                    etAddress.setText(str(d, "address"));
                    etCity.setText(str(d, "city"));
                    etGst.setText(str(d, "gst_number"));
                } catch (Exception e) {
                    Log.e(TAG, "Load error", e);
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                AppToast.show(ShopProfileActivity.this, "Network error");
            }
        });
    }

    private void save() {
        if (etName.getText().toString().trim().isEmpty()) {
            etName.setError("Shop ka naam daalein");
            return;
        }
        Map<String, String> body = new HashMap<>();
        body.put("name", etName.getText().toString().trim());
        body.put("address", etAddress.getText().toString().trim());
        body.put("city", etCity.getText().toString().trim());
        body.put("gst_number", etGst.getText().toString().trim());
        ApiClient.get(this).updateShopProfile(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                if (res.isSuccessful()) {
                    AppToast.show(ShopProfileActivity.this, " Save ho gaya");
                } else {
                    AppToast.show(ShopProfileActivity.this, "Save nahi hua");
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                AppToast.show(ShopProfileActivity.this, "Network error");
            }
        });
    }

    private String str(JsonObject o, String key) {
        return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString() : "";
    }
}
