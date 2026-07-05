package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.os.Bundle;
import android.util.Log;
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

/** WhatsApp/SMS Templates — customer ko bheje jaane wale messages customize karein */
public class TemplatesActivity extends AppCompatActivity {

    private static final String TAG = "TemplatesActivity";
    private EditText etReceived, etReady, etDelivered;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_templates);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        etReceived = findViewById(R.id.etReceived);
        etReady = findViewById(R.id.etReady);
        etDelivered = findViewById(R.id.etDelivered);
        findViewById(R.id.btnSave).setOnClickListener(v -> save());

        load();
    }

    private void load() {
        ApiClient.get(this).shopProfile().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                if (!res.isSuccessful() || res.body() == null) return;
                try {
                    JsonObject d = res.body();
                    if (d.has("template_received")) etReceived.setText(d.get("template_received").getAsString());
                    if (d.has("template_ready")) etReady.setText(d.get("template_ready").getAsString());
                    if (d.has("template_delivered")) etDelivered.setText(d.get("template_delivered").getAsString());
                } catch (Exception e) {
                    Log.e(TAG, "Load error", e);
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                AppToast.show(TemplatesActivity.this, "Network error");
            }
        });
    }

    private void save() {
        Map<String, String> body = new HashMap<>();
        body.put("template_received", etReceived.getText().toString().trim());
        body.put("template_ready", etReady.getText().toString().trim());
        body.put("template_delivered", etDelivered.getText().toString().trim());
        ApiClient.get(this).updateTemplates(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                AppToast.show(TemplatesActivity.this, res.isSuccessful() ? " Templates save hue" : "Save nahi hua");
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                AppToast.show(TemplatesActivity.this, "Network error");
            }
        });
    }
}
