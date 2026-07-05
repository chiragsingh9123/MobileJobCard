package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.os.Bundle;
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

/** Security: Password change karein */
public class ChangePasswordActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        EditText etOld = findViewById(R.id.etOld);
        EditText etNew = findViewById(R.id.etNew);
        EditText etConfirm = findViewById(R.id.etConfirm);
        Button_save(etOld, etNew, etConfirm);
    }

    private void Button_save(EditText etOld, EditText etNew, EditText etConfirm) {
        findViewById(R.id.btnSave).setOnClickListener(v -> {
            String oldPw = etOld.getText().toString();
            String newPw = etNew.getText().toString();
            String confirmPw = etConfirm.getText().toString();

            if (oldPw.isEmpty()) { etOld.setError("Purana password daalein"); return; }
            if (newPw.length() < 4) { etNew.setError("Kam se kam 4 characters"); return; }
            if (!newPw.equals(confirmPw)) { etConfirm.setError("Match nahi kar raha"); return; }

            Map<String, String> body = new HashMap<>();
            body.put("old_password", oldPw);
            body.put("new_password", newPw);
            ApiClient.get(this).changePassword(body).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                    if (res.isSuccessful() && res.body() != null) {
                        AppToast.show(ChangePasswordActivity.this, " Password change ho gaya");
                        finish();
                    } else {
                        AppToast.show(ChangePasswordActivity.this, " Purana password galat hai");
                    }
                }
                @Override
                public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                    AppToast.show(ChangePasswordActivity.this, "Network error");
                }
            });
        });
    }
}
