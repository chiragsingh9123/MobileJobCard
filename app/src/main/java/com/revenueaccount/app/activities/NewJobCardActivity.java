package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.ViewFlipper;
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

/** Screen 5: New Job Card — 4-step wizard (Customer → Device → Problem → Summary)
 * Mobile ke 10 digit poore hote hi purana customer AUTO-FETCH
 * IMEI barcode scan (camera se seedha IMEI number fill ho jaata hai)
 * Pattern/PIN lock entry */
public class NewJobCardActivity extends AppCompatActivity {

    private static final String TAG = "NewJobCardActivity";
    private static final int CAMERA_PERMISSION_REQUEST = 501;

    private final String[] STEPS = {"Customer", "Device", "Problem", "Summary"};
    private final String[] NEXT_LABELS = {"Next: Device Information",
        "Next: Problem & Estimate", "Next: Summary", " Save Job Card"};

    private ViewFlipper flipper;
    private Button btnNext, btnPrev;
    private LinearLayout stepIndicator;
    private int step = 0;

    private EditText etMobile, etName, etAddress, etCity, etEmail, etAlternateMobile;
    private EditText etBrand, etModel, etImei1, etImei2, etColor, etStorage, etPassword, etAccessories, etCustomName;
    private EditText etProblem, etCost, etAdvance, etDelivery;
    private TextView tvCustomerFound, tvSummary;
    private RadioGroup rgLockType;
    private View tilPin, patternContainer;
    private PatternLockView patternLockView;
    private TextView tvPatternStatus;
    private String patternCode = "";

    private long existingCustomerId = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable lookupRunnable;

    /** Jis field me scan ka result jaana hai, wo yahan track hota hai */
    private EditText scanTargetField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_job_card);

        flipper = findViewById(R.id.flipper);
        btnNext = findViewById(R.id.btnNext);
        btnPrev = findViewById(R.id.btnPrev);
        stepIndicator = findViewById(R.id.stepIndicator);
        findViewById(R.id.btnBack).setOnClickListener(v -> onBackPressed());

        etMobile = findViewById(R.id.etMobile); etName = findViewById(R.id.etName);
        etAddress = findViewById(R.id.etAddress); etCity = findViewById(R.id.etCity);
        etEmail = findViewById(R.id.etEmail); etAlternateMobile = findViewById(R.id.etAlternateMobile);
        etCustomName = findViewById(R.id.etCustomName);
        etBrand = findViewById(R.id.etBrand); etModel = findViewById(R.id.etModel);
        etImei1 = findViewById(R.id.etImei1); etImei2 = findViewById(R.id.etImei2);
        etColor = findViewById(R.id.etColor); etStorage = findViewById(R.id.etStorage);
        etPassword = findViewById(R.id.etPassword); etAccessories = findViewById(R.id.etAccessories);
        etProblem = findViewById(R.id.etProblem); etCost = findViewById(R.id.etCost);
        etAdvance = findViewById(R.id.etAdvance); etDelivery = findViewById(R.id.etDelivery);
        tvCustomerFound = findViewById(R.id.tvCustomerFound);
        tvSummary = findViewById(R.id.tvSummary);

        setupLockTypeUI();

        // IMEI scan buttons
        findViewById(R.id.btnScanImei1).setOnClickListener(v -> startScan(etImei1));
        findViewById(R.id.btnScanImei2).setOnClickListener(v -> startScan(etImei2));

        buildStepIndicator();
        setupAutoFetch();

        btnNext.setOnClickListener(v -> {
            if (!validateStep()) return;
            if (step == 3) { saveJob(); return; }
            step++;
            if (step == 3) buildSummary();
            updateStep();
        });
        btnPrev.setOnClickListener(v -> { if (step > 0) { step--; updateStep(); } });
    }

    // ================= LOCK TYPE (None / PIN / Pattern) =================

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

    // ================= IMEI BARCODE SCAN =================

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
            AppToast.show(this, "Scanner start nahi ho paya");
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
                AppToast.show(this, "IMEI scan karne ke liye camera permission chahiye");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
            if (result != null && result.getContents() != null && scanTargetField != null) {
                // Barcode me kabhi-kabhi extra spaces/letters aa sakte hain, sirf digits rakhein
                String scanned = result.getContents().replaceAll("[^0-9]", "");
                if (scanned.length() >= 14) {
                    scanTargetField.setText(scanned.length() > 15 ? scanned.substring(0, 15) : scanned);
                    AppToast.show(this, " IMEI scan ho gaya");
                } else if (!scanned.isEmpty()) {
                    scanTargetField.setText(scanned);
                    AppToast.show(this, " Barcode me poora IMEI nahi mila, check kar lein");
                } else {
                    AppToast.show(this, "Yeh IMEI jaisa barcode nahi laga");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Scan result parse error", e);
        }
    }

    // ================= AUTO-FETCH =================

    /** AUTO-FETCH: 10 digit poore hote hi API call (400ms debounce) */
    private void setupAutoFetch() {
        etMobile.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {
                if (lookupRunnable != null) handler.removeCallbacks(lookupRunnable);
                existingCustomerId = -1;
                tvCustomerFound.setVisibility(View.GONE);
                if (s.length() == 10) {
                    lookupRunnable = () -> lookup(s.toString());
                    handler.postDelayed(lookupRunnable, 400);
                }
            }
        });
    }

    private void lookup(String mobile) {
        ApiClient.get(this).lookupCustomer(mobile).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                try {
                    if (res.isSuccessful() && res.body() != null
                    && res.body().has("found") && res.body().get("found").getAsBoolean()) {
                        JsonObject c = res.body().getAsJsonObject("customer");
                        existingCustomerId = c.get("id").getAsLong();
                        etName.setText(str(c, "name"));
                        etAddress.setText(str(c, "address"));
                        etCity.setText(str(c, "city"));
                        etEmail.setText(str(c, "email"));
                        double khata = c.has("khata_balance") ? c.get("khata_balance").getAsDouble() : 0;
                        String msg = " Purana customer: " + str(c, "name")
                        + " • Total jobs: " + (c.has("total_jobs") ? c.get("total_jobs").getAsInt() : 0);
                        if (khata > 0) msg += "\n Khata baaki: ₹" + khata;
                        tvCustomerFound.setText(msg);
                        tvCustomerFound.setVisibility(View.VISIBLE);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Lookup render error", e);
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {}
        });
    }

    private String str(JsonObject o, String key) {
        return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString() : "";
    }

    // ================= WIZARD STEPS =================

    private void buildStepIndicator() {
        stepIndicator.removeAllViews();
        for (int i = 0; i < STEPS.length; i++) {
            TextView tv = new TextView(this);
            tv.setText((i + 1) + ". " + STEPS[i]);
            tv.setTextSize(12f);
            tv.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp =
            new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tv.setLayoutParams(lp);
            stepIndicator.addView(tv);
        }
        updateStep();
    }

    private void updateStep() {
        flipper.setDisplayedChild(step);
        btnNext.setText(NEXT_LABELS[step]);
        btnPrev.setVisibility(step == 0 ? View.INVISIBLE : View.VISIBLE);
        btnNext.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
        Color.parseColor(step == 3 ? "#4CAF50" : "#1565C0")));
        for (int i = 0; i < stepIndicator.getChildCount(); i++) {
            TextView tv = (TextView) stepIndicator.getChildAt(i);
            boolean active = i == step;
            tv.setTextColor(Color.parseColor(active ? "#1565C0" : (i < step ? "#4CAF50" : "#9E9E9E")));
            tv.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private boolean validateStep() {
        if (step == 0) {
            if (etMobile.length() != 10) { etMobile.setError("10 digit mobile daalein"); return false; }
            if (etName.length() == 0) { etName.setError("Naam daalein"); return false; }
        } else if (step == 1) {
            if (etBrand.length() == 0) { etBrand.setError("Brand daalein"); return false; }
            if (etModel.length() == 0) { etModel.setError("Model daalein"); return false; }
        } else if (step == 2) {
            if (etProblem.length() == 0) { etProblem.setError("Problem likhein"); return false; }
            if (etCost.length() == 0) { etCost.setError("Estimated cost daalein"); return false; }
            if (NumUtils.parseDouble(etCost.getText().toString()) <= 0) {
                etCost.setError("Sahi amount daalein"); return false;
            }
        }
        return true;
    }

    private void buildSummary() {
        String s = " " + etName.getText() + " (" + etMobile.getText() + ")"
        + "\n " + etBrand.getText() + " " + etModel.getText()
        + (etColor.length() > 0 ? " • " + etColor.getText() : "")
        + (etStorage.length() > 0 ? " • " + etStorage.getText() : "")
        + "\n " + etProblem.getText()
        + "\n Estimate: ₹" + etCost.getText()
        + (etAdvance.length() > 0 ? " | Advance: ₹" + etAdvance.getText() : "")
        + (etDelivery.length() > 0 ? "\n Delivery: " + etDelivery.getText() : "");
        tvSummary.setText(s);
    }

    private void saveJob() {
        btnNext.setEnabled(false);
        JsonObject body = new JsonObject();
        if (existingCustomerId > 0) {
            body.addProperty("customer_id", existingCustomerId);
        } else {
            JsonObject cd = new JsonObject();
            cd.addProperty("name", etName.getText().toString().trim());
            cd.addProperty("mobile", etMobile.getText().toString().trim());
            cd.addProperty("address", etAddress.getText().toString().trim());
            cd.addProperty("city", etCity.getText().toString().trim());
            cd.addProperty("email", etEmail.getText().toString().trim());
            body.add("customer_data", cd);
        }
        body.addProperty("device_brand", etBrand.getText().toString().trim());
        body.addProperty("device_model", etModel.getText().toString().trim());
        body.addProperty("custom_name", etCustomName.getText().toString().trim());
        body.addProperty("imei1", etImei1.getText().toString().trim());
        body.addProperty("imei2", etImei2.getText().toString().trim());
        body.addProperty("color", etColor.getText().toString().trim());
        body.addProperty("storage", etStorage.getText().toString().trim());
        body.addProperty("lock_type", currentLockType());
        body.addProperty("device_password", currentLockValue());
        body.addProperty("accessories", etAccessories.getText().toString().trim());
        body.addProperty("alternate_mobile", etAlternateMobile.getText().toString().trim());
        body.addProperty("problem", etProblem.getText().toString().trim());
        body.addProperty("estimated_cost", NumUtils.parseDouble(etCost.getText().toString()));
        body.addProperty("expected_delivery", etDelivery.getText().toString().trim());

        ApiClient.get(this).createJob(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                btnNext.setEnabled(true);
                if (res.isSuccessful() && res.body() != null) {
                    try {
                        JsonObject job = res.body();
                        double adv = NumUtils.parseDouble(etAdvance.getText().toString());
                        if (adv > 0) {
                            recordAdvance(job, adv);
                        } else {
                            done(job.has("job_id") ? job.get("job_id").getAsString() : "");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Save-job parse error", e);
                        AppToast.show(NewJobCardActivity.this, "Job ban gaya, par kuch data dikhane me dikkat");
                        finish();
                    }
                } else {
                    AppToast.show(NewJobCardActivity.this, "Job save nahi hui, dubara try karein");
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                btnNext.setEnabled(true);
                AppToast.show(NewJobCardActivity.this, "Network error");
            }
        });
    }

    private void recordAdvance(JsonObject job, double amount) {
        try {
            JsonObject p = new JsonObject();
            p.addProperty("amount", amount);
            p.addProperty("customer_id", job.getAsJsonObject("customer").get("id").getAsLong());
            p.addProperty("job_card_id", job.get("id").getAsLong());
            p.addProperty("payment_type", "ADVANCE");
            p.addProperty("method", "CASH");
            ApiClient.get(this).createPayment(p).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(@NonNull Call<JsonObject> c, @NonNull Response<JsonObject> r) {
                    done(job.has("job_id") ? job.get("job_id").getAsString() : "");
                }
                @Override
                public void onFailure(@NonNull Call<JsonObject> c, @NonNull Throwable t) {
                    done(job.has("job_id") ? job.get("job_id").getAsString() : "");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Record-advance error", e);
            done(job.has("job_id") ? job.get("job_id").getAsString() : "");
        }
    }

    private void done(String jobId) {
        AppToast.show(this, " Job Card ban gaya: " + jobId);
        finish();
    }
}
