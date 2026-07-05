package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.gson.JsonObject;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.revenueaccount.app.R;
import com.revenueaccount.app.api.ApiClient;
import com.revenueaccount.app.utils.NumUtils;
import com.revenueaccount.app.widgets.PatternLockView;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Job Details kabhi bhi edit kiye ja sakte hain — device info, problem, cost, lock, sab kuch */
public class EditJobActivity extends AppCompatActivity {

    private static final String TAG = "EditJobActivity";
    private static final int CAMERA_PERMISSION_REQUEST = 502;

    private long jobPk;
    private EditText etCustomName, etBrand, etModel, etImei1, etImei2, etColor, etStorage,
    etPassword, etAccessories, etAlternateMobile, etProblem, etDiagnosis, etCost, etDelivery;
    private RadioGroup rgLockType;
    private View tilPin, patternContainer;
    private PatternLockView patternLockView;
    private TextView tvPatternStatus;
    private String patternCode = "";
    private EditText scanTargetField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_job);
        jobPk = getIntent().getLongExtra("job_pk", -1);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        etCustomName = findViewById(R.id.etCustomName);
        etBrand = findViewById(R.id.etBrand); etModel = findViewById(R.id.etModel);
        etImei1 = findViewById(R.id.etImei1); etImei2 = findViewById(R.id.etImei2);
        etColor = findViewById(R.id.etColor); etStorage = findViewById(R.id.etStorage);
        etPassword = findViewById(R.id.etPassword); etAccessories = findViewById(R.id.etAccessories);
        etAlternateMobile = findViewById(R.id.etAlternateMobile);
        etProblem = findViewById(R.id.etProblem); etDiagnosis = findViewById(R.id.etDiagnosis);
        etCost = findViewById(R.id.etCost); etDelivery = findViewById(R.id.etDelivery);

        setupLockTypeUI();
        findViewById(R.id.btnScanImei1).setOnClickListener(v -> startScan(etImei1));
        findViewById(R.id.btnScanImei2).setOnClickListener(v -> startScan(etImei2));
        findViewById(R.id.btnSave).setOnClickListener(v -> save());

        loadJob();
    }

    private void loadJob() {
        ApiClient.get(this).jobDetails(jobPk).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                if (res.isSuccessful() && res.body() != null) {
                    try {
                        populate(res.body());
                    } catch (Exception e) {
                        Log.e(TAG, "Populate error", e);
                        AppToast.error(EditJobActivity.this, "Data load karne me dikkat aayi");
                    }
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                AppToast.error(EditJobActivity.this, "Network error");
            }
        });
    }

    private void populate(JsonObject job) {
        etCustomName.setText(str(job, "custom_name"));
        etBrand.setText(str(job, "device_brand"));
        etModel.setText(str(job, "device_model"));
        etImei1.setText(str(job, "imei1"));
        etImei2.setText(str(job, "imei2"));
        etColor.setText(str(job, "color"));
        etStorage.setText(str(job, "storage"));
        etAccessories.setText(str(job, "accessories"));
        etAlternateMobile.setText(str(job, "alternate_mobile"));
        etProblem.setText(str(job, "problem"));
        etDiagnosis.setText(str(job, "diagnosis"));
        etCost.setText(job.has("estimated_cost") ? job.get("estimated_cost").getAsString() : "0");
        etDelivery.setText(str(job, "expected_delivery"));

        String lockType = str(job, "lock_type");
        String lockValue = str(job, "device_password");
        if ("PIN".equals(lockType)) {
            rgLockType.check(R.id.rbLockPin);
            etPassword.setText(lockValue);
        } else if ("PATTERN".equals(lockType)) {
            rgLockType.check(R.id.rbLockPattern);
            patternCode = lockValue;
            patternLockView.setPatternCode(lockValue);
            tvPatternStatus.setText("Saved pattern (dobara draw karke badlein)");
        } else {
            rgLockType.check(R.id.rbLockNone);
        }
    }

    private String str(JsonObject o, String key) {
        return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString() : "";
    }

    private void save() {
        if (etBrand.length() == 0 || etModel.length() == 0) {
            AppToast.warning(this, "Brand aur Model daalna zaroori hai");
            return;
        }
        if (etProblem.length() == 0) {
            AppToast.warning(this, "Problem daalna zaroori hai");
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("custom_name", etCustomName.getText().toString().trim());
        body.addProperty("device_brand", etBrand.getText().toString().trim());
        body.addProperty("device_model", etModel.getText().toString().trim());
        body.addProperty("imei1", etImei1.getText().toString().trim());
        body.addProperty("imei2", etImei2.getText().toString().trim());
        body.addProperty("color", etColor.getText().toString().trim());
        body.addProperty("storage", etStorage.getText().toString().trim());
        body.addProperty("lock_type", currentLockType());
        body.addProperty("device_password", currentLockValue());
        body.addProperty("accessories", etAccessories.getText().toString().trim());
        body.addProperty("alternate_mobile", etAlternateMobile.getText().toString().trim());
        body.addProperty("problem", etProblem.getText().toString().trim());
        body.addProperty("diagnosis", etDiagnosis.getText().toString().trim());
        body.addProperty("estimated_cost", NumUtils.parseDouble(etCost.getText().toString()));
        body.addProperty("expected_delivery", etDelivery.getText().toString().trim());

        Button btn = findViewById(R.id.btnSave);
        btn.setEnabled(false);
        ApiClient.get(this).updateJob(jobPk, body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                btn.setEnabled(true);
                if (res.isSuccessful()) {
                    AppToast.success(EditJobActivity.this, " Job details update ho gaye");
                    setResult(RESULT_OK);
                    finish();
                } else {
                    AppToast.error(EditJobActivity.this, "Update nahi hua, dubara try karein");
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                btn.setEnabled(true);
                AppToast.error(EditJobActivity.this, "Network error");
            }
        });
    }

    // ================= LOCK TYPE (shared logic with NewJobCardActivity) =================

    private void setupLockTypeUI() {
        rgLockType = findViewById(R.id.rgLockType);
        tilPin = findViewById(R.id.tilPin);
        patternContainer = findViewById(R.id.patternContainer);
        patternLockView = findViewById(R.id.patternLockView);
        tvPatternStatus = findViewById(R.id.tvPatternStatus);

        rgLockType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbLockPin) {
                tilPin.setVisibility(View.VISIBLE);
                patternContainer.setVisibility(View.GONE);
            } else if (checkedId == R.id.rbLockPattern) {
                tilPin.setVisibility(View.GONE);
                patternContainer.setVisibility(View.VISIBLE);
            } else {
                tilPin.setVisibility(View.GONE);
                patternContainer.setVisibility(View.GONE);
            }
        });

        patternLockView.setOnPatternChangeListener(code -> {
            patternCode = code;
            int dots = code.isEmpty() ? 0 : code.split("-").length;
            tvPatternStatus.setText(dots >= 4 ? "Pattern set (" + dots + " dots)"
            : "Kam se kam 4 dots milayein");
        });

        findViewById(R.id.btnClearPattern).setOnClickListener(v -> {
            patternLockView.clear();
            patternCode = "";
            tvPatternStatus.setText("Pattern draw karein");
        });
    }

    private String currentLockType() {
        int id = rgLockType.getCheckedRadioButtonId();
        if (id == R.id.rbLockPin) return "PIN";
        if (id == R.id.rbLockPattern) return "PATTERN";
        return "NONE";
    }

    private String currentLockValue() {
        String type = currentLockType();
        if ("PIN".equals(type)) return etPassword.getText().toString().trim();
        if ("PATTERN".equals(type)) return patternCode;
        return "";
    }

    // ================= IMEI SCAN =================

    private void startScan(EditText target) {
        scanTargetField = target;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
            new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            return;
        }
        launchScanner();
    }

    private void launchScanner() {
        try {
            IntentIntegrator integrator = new IntentIntegrator(this);
            integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
            integrator.setPrompt("IMEI barcode ko frame ke andar rakhein");
            integrator.setBeepEnabled(true);
            integrator.setOrientationLocked(true);
            integrator.setCaptureActivity(com.journeyapps.barcodescanner.CaptureActivity.class);
            integrator.initiateScan();
        } catch (Exception e) {
            Log.e(TAG, "Scanner launch failed", e);
            AppToast.error(this, "Scanner start nahi ho paya");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
    @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchScanner();
            } else {
                AppToast.warning(this, "IMEI scan karne ke liye camera permission chahiye");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
            if (result != null && result.getContents() != null && scanTargetField != null) {
                String scanned = result.getContents().replaceAll("[^0-9]", "");
                if (!scanned.isEmpty()) {
                    scanTargetField.setText(scanned.length() > 15 ? scanned.substring(0, 15) : scanned);
                    AppToast.success(this, " IMEI scan ho gaya");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Scan result parse error", e);
        }
    }
}
