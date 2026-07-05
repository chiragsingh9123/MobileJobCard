package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.google.gson.JsonObject;
import com.revenueaccount.app.R;
import com.revenueaccount.app.api.ApiClient;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Backup & Restore — shop ka poora data JSON export karke share karein */
public class BackupActivity extends AppCompatActivity {

    private static final String TAG = "BackupActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        Button btnExport = findViewById(R.id.btnExport);
        btnExport.setOnClickListener(v -> exportBackup(btnExport));
    }

    private void exportBackup(Button btn) {
        btn.setEnabled(false);
        ApiClient.get(this).exportBackup().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                btn.setEnabled(true);
                if (res.isSuccessful() && res.body() != null) {
                    try {
                        saveAndShare(res.body().toString());
                    } catch (Exception e) {
                        Log.e(TAG, "Export save error", e);
                        AppToast.show(BackupActivity.this, "File save nahi hui");
                    }
                } else {
                    AppToast.show(BackupActivity.this, "Backup export nahi hua");
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                btn.setEnabled(true);
                AppToast.show(BackupActivity.this, "Network error");
            }
        });
    }

    private void saveAndShare(String json) throws Exception {
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.getDefault()).format(new Date());
        File dir = new File(getCacheDir(), "backups");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "revenue_account_backup_" + stamp + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(json);
        }
        android.net.Uri uri = FileProvider.getUriForFile(this,
        getPackageName() + ".fileprovider", file);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/json");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "Backup kahan save/share karein?"));
    }
}
