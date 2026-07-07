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

/** Job Details kabhi bhi edit kiye ja sakte hain - device info (single Model field),
 * problem, cost, lock (None/PIN/Password/Pattern), sab kuch */
public class EditJobActivity extends AppCompatActivity {

    private static final String TAG = "EditJobActivity";
    private static final int CAMERA_PERMISSION_REQUEST = 502;

    private long jobPk;
    private EditText etModel, etImei1, etImei2,
            etPassword, etPasswordText, etAccessories, etAlternateMobile,
            etProblem, etDiagnosis, etCost, etDelivery;
    private RadioGroup rgLockType;
    private View tilPin, tilPasswordText, patternContainer;
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

        etModel = findViewById(R.id.etModel);
        etImei1 = findViewById(R.id.etImei1); etImei2 = findViewById(R.id.etImei2);
        etPassword = findViewById(R.id.etPassword);
        etPasswordText = findViewById(R.id.etPasswordText);
        etAccessories = findViewById(R.id.etAccessories);
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
                        AppToast.error(EditJobActivity.this, "There was a problem loading the data");
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
        etModel.setText(str(job, "device_model"));
        etImei1.setText(str(job, "imei1"));
        etImei2.setText(str(job, "imei2"));
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
        } else if ("PASSWORD".equals(lockType)) {
            rgLockType.check(R.id.rbLockPassword);
            etPasswordText.setText(lockValue);
        } else if ("PATTERN".equals(lockType)) {
            rgLockType.check(R.id.rbLockPattern);
            patternCode = lockValue;
            patternLockView.setPatternCode(lockValue);
            tvPatternStatus.setText("Saved pattern (draw again to change it)");
        } else {
            rgLockType.check(R.id.rbLockNone);
        }
    }

    private String str(JsonObject o, String key) {
        return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString() : "";
    }

    private void save() {
        if (etModel.length() == 0) {
            AppToast.warning(this, "Device Model daalna is required");
            return;
        }
        if (etProblem.length() == 0) {
            AppToast.warning(this, "Problem daalna is required");
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("device_model", etModel.getText().toString().trim());
        body.addProperty("imei1", etImei1.getText().toString().trim());
        body.addProperty("imei2", etImei2.getText().toString().trim());
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
                    AppToast.success(EditJobActivity.this, "Job details update ho gaye");
                    setResult(RESULT_OK);
                    finish();
                } else {
                    AppToast.error(EditJobActivity.this, "The update failed, please try again");
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                btn.setEnabled(true);
                AppToast.error(EditJobActivity.this, "Network error");
            }
        });
    }

    // ================= LOCK TYPE (None / PIN / Password / Pattern) =================

    private void setupLockTypeUI() {
        rgLockType = findViewById(R.id.rgLockType);
        tilPin = findViewById(R.id.tilPin);
        tilPasswordText = findViewById(R.id.tilPasswordText);
        patternContainer = findViewById(R.id.patternContainer);
        patternLockView = findViewById(R.id.patternLockView);
        tvPatternStatus = findViewById(R.id.tvPatternStatus);

        rgLockType.setOnCheckedChangeListener((group, checkedId) -> {
            tilPin.setVisibility(checkedId == R.id.rbLockPin ? View.VISIBLE : View.GONE);
            tilPasswordText.setVisibility(checkedId == R.id.rbLockPassword ? View.VISIBLE : View.GONE);
            patternContainer.setVisibility(checkedId == R.id.rbLockPattern ? View.VISIBLE : View.GONE);
        });

        patternLockView.setOnPatternChangeListener(code -> {
            patternCode = code;
            int dots = code.isEmpty() ? 0 : code.split("-").length;
            tvPatternStatus.setText(dots >= 4 ? "Pattern set (" + dots + " dots)"
                    : "Connect at least 4 dots");
        });

        findViewById(R.id.btnClearPattern).setOnClickListener(v -> {
            patternLockView.clear();
            patternCode = "";
            tvPatternStatus.setText("Draw your pattern");
        });
    }

    private String currentLockType() {
        int id = rgLockType.getCheckedRadioButtonId();
        if (id == R.id.rbLockPin) return "PIN";
        if (id == R.id.rbLockPassword) return "PASSWORD";
        if (id == R.id.rbLockPattern) return "PATTERN";
        return "NONE";
    }

    private String currentLockValue() {
        String type = currentLockType();
        if ("PIN".equals(type)) return etPassword.getText().toString().trim();
        if ("PASSWORD".equals(type)) return etPasswordText.getText().toString().trim();
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
            integrator.setCaptureActivity(com.revenueaccount.app.scanner.PortraitCaptureActivity.class);
            integrator.initiateScan();
        } catch (Exception e) {
            Log.e(TAG, "Scanner launch failed", e);
            AppToast.error(this, "Could not start the scanner");
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
                AppToast.warning(this, "Camera permission is required to scan the IMEI");
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
                    AppToast.success(this, "IMEI scanned successfully");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Scan result parse error", e);
        }
    }
}
