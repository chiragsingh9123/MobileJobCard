package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.gson.JsonObject;
import com.revenueaccount.app.R;
import com.revenueaccount.app.api.ApiClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Print/Receipt Settings — job receipt me header/footer/GST dikhana */
public class PrintSettingsActivity extends AppCompatActivity {

    private static final String TAG = "PrintSettingsActivity";
    private EditText etHeader, etFooter;
    private MaterialSwitch switchGst;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_print_settings);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        etHeader = findViewById(R.id.etHeader);
        etFooter = findViewById(R.id.etFooter);
        switchGst = findViewById(R.id.switchGst);
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
                    if (d.has("receipt_header")) etHeader.setText(d.get("receipt_header").getAsString());
                    if (d.has("receipt_footer")) etFooter.setText(d.get("receipt_footer").getAsString());
                    if (d.has("show_gst_on_receipt")) switchGst.setChecked(d.get("show_gst_on_receipt").getAsBoolean());
                } catch (Exception e) {
                    Log.e(TAG, "Load error", e);
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                AppToast.show(PrintSettingsActivity.this, "Network error");
            }
        });
    }

    private void save() {
        JsonObject body = new JsonObject();
        body.addProperty("receipt_header", etHeader.getText().toString().trim());
        body.addProperty("receipt_footer", etFooter.getText().toString().trim());
        body.addProperty("show_gst_on_receipt", switchGst.isChecked());
        ApiClient.get(this).updatePrintSettings(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                AppToast.show(PrintSettingsActivity.this, res.isSuccessful() ? "Saved successfully" : "Save failed");
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                AppToast.show(PrintSettingsActivity.this, "Network error");
            }
        });
    }
}
