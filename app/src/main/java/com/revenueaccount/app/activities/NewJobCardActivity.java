package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ViewFlipper;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.gson.JsonObject;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.revenueaccount.app.R;
import com.revenueaccount.app.api.ApiClient;
import com.revenueaccount.app.scanner.PortraitCaptureActivity;
import com.revenueaccount.app.utils.NumUtils;
import com.revenueaccount.app.utils.StaffPickerHelper;
import com.revenueaccount.app.widgets.PatternLockView;
import java.io.File;
import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Screen 5: New Job Card - 4-step wizard (Customer -> Device -> Problem -> Summary)
 * - Returning-customer auto-fetch once the 10-digit mobile number is complete
 * - IMEI barcode scanning
 * - PIN / Password / Pattern lock entry
 * - Aadhaar and Bill/Box customer documentation (photos + statement videos)
 * - A "who is saving this job card" confirmation before the job is created */
public class NewJobCardActivity extends AppCompatActivity {

    private static final String TAG = "NewJobCardActivity";
    private static final int CAMERA_PERMISSION_REQUEST = 501;
    private static final int REQ_CAPTURE_PHOTO = 801;
    private static final int REQ_CAPTURE_VIDEO = 802;
    private static final int REQ_PICK_PHOTO = 803;
    private static final int REQ_PICK_VIDEO = 804;

    private static final int SLOT_AADHAAR_FRONT = 1;
    private static final int SLOT_AADHAAR_BACK = 2;
    private static final int SLOT_AADHAAR_VIDEO = 3;
    private static final int SLOT_BILL_PHOTO = 4;
    private static final int SLOT_BOX_PHOTO = 5;
    private static final int SLOT_BILL_VIDEO = 6;

    private final String[] STEPS = {"Customer", "Device", "Problem", "Summary"};
    private final String[] NEXT_LABELS = {"Next: Device Information",
            "Next: Problem & Estimate", "Next: Summary", "Save Job Card"};

    private ViewFlipper flipper;
    private Button btnNext, btnPrev;
    private LinearLayout stepIndicator;
    private int step = 0;

    private EditText etMobile, etName, etAddress, etAlternateMobile;
    private EditText etModel, etImei1, etImei2, etPassword, etPasswordText, etAccessories;
    private EditText etProblem, etCost, etAdvance, etDelivery;
    private EditText etAadhaarNumber, etBillNumber;
    private TextView tvCustomerFound, tvSummary;
    private RadioGroup rgLockType;
    private View tilPin, tilPasswordText, patternContainer;
    private PatternLockView patternLockView;
    private TextView tvPatternStatus;
    private String patternCode = "";

    private long existingCustomerId = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable lookupRunnable;

    private EditText scanTargetField;

    // Documentation capture state
    private MaterialButtonToggleGroup toggleDocTab;
    private View sectionAadhaar, sectionBill;
    private Uri aadhaarFrontUri, aadhaarBackUri, aadhaarVideoUri;
    private Uri billPhotoUri, boxPhotoUri, billVideoUri;
    private int activeCaptureSlot = 0;
    private Uri pendingCaptureUri;
    private final java.util.Map<Integer, Integer> slotIncludeIds = new java.util.HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_job_card);

        flipper = findViewById(R.id.flipper);
        btnNext = findViewById(R.id.btnNext);
        btnPrev = findViewById(R.id.btnPrev);
        stepIndicator = findViewById(R.id.stepIndicator);
        findViewById(R.id.btnBack).setOnClickListener(v -> onBackPressed());

        etMobile = findViewById(R.id.etMobile);
        etAlternateMobile = findViewById(R.id.etAlternateMobile);
        etName = findViewById(R.id.etName);
        etAddress = findViewById(R.id.etAddress);
        etModel = findViewById(R.id.etModel);
        etImei1 = findViewById(R.id.etImei1); etImei2 = findViewById(R.id.etImei2);
        etPassword = findViewById(R.id.etPassword);
        etPasswordText = findViewById(R.id.etPasswordText);
        etAccessories = findViewById(R.id.etAccessories);
        etProblem = findViewById(R.id.etProblem); etCost = findViewById(R.id.etCost);
        etAdvance = findViewById(R.id.etAdvance); etDelivery = findViewById(R.id.etDelivery);
        etAadhaarNumber = findViewById(R.id.etAadhaarNumber);
        etBillNumber = findViewById(R.id.etBillNumber);
        tvCustomerFound = findViewById(R.id.tvCustomerFound);
        tvSummary = findViewById(R.id.tvSummary);

        setupLockTypeUI();
        setupDocumentationTabs();
        setupCaptureSlots();

        findViewById(R.id.btnScanImei1).setOnClickListener(v -> startScan(etImei1));
        findViewById(R.id.btnScanImei2).setOnClickListener(v -> startScan(etImei2));

        buildStepIndicator();
        setupAutoFetch();

        btnNext.setOnClickListener(v -> {
            if (!validateStep()) return;
            if (step == 3) { showSaveConfirmDialog(); return; }
            step++;
            if (step == 3) buildSummary();
            updateStep();
        });
        btnPrev.setOnClickListener(v -> { if (step > 0) { step--; updateStep(); } });
    }

    // ================= DOCUMENTATION TABS (Aadhaar / Bill & Box) =================

    private void setupDocumentationTabs() {
        toggleDocTab = findViewById(R.id.toggleDocTab);
        sectionAadhaar = findViewById(R.id.sectionAadhaar);
        sectionBill = findViewById(R.id.sectionBill);

        toggleDocTab.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            boolean aadhaarTab = checkedId == R.id.btnTabAadhaar;
            sectionAadhaar.setVisibility(aadhaarTab ? View.VISIBLE : View.GONE);
            sectionBill.setVisibility(aadhaarTab ? View.GONE : View.VISIBLE);
        });
    }

    private void setupCaptureSlots() {
        setupSlot(R.id.slotAadhaarFront, R.drawable.ic_photo, "Front Photo", SLOT_AADHAAR_FRONT, false);
        setupSlot(R.id.slotAadhaarBack, R.drawable.ic_photo, "Back Photo", SLOT_AADHAAR_BACK, false);
        setupSlot(R.id.slotAadhaarVideo, R.drawable.ic_video, "Statement Video", SLOT_AADHAAR_VIDEO, true);
        setupSlot(R.id.slotBillPhoto, R.drawable.ic_photo, "Bill Photo", SLOT_BILL_PHOTO, false);
        setupSlot(R.id.slotBoxPhoto, R.drawable.ic_photo, "Box Photo", SLOT_BOX_PHOTO, false);
        setupSlot(R.id.slotBillVideo, R.drawable.ic_video, "Statement Video", SLOT_BILL_VIDEO, true);
    }

    private void setupSlot(int includeId, int icon, String label, int slot, boolean isVideo) {
        View slotView = findViewById(includeId);
        if (slotView == null) return;
        slotIncludeIds.put(slot, includeId);
        ((ImageView) slotView.findViewById(R.id.ivSlotIcon)).setImageResource(icon);
        ((TextView) slotView.findViewById(R.id.tvSlotLabel)).setText(label);
        slotView.setOnClickListener(v -> {
            Uri existing = getSlotUri(slot);
            if (existing != null) {
                showSlotPreview(existing, slot, isVideo);
            } else {
                showCaptureChooser(slot, isVideo);
            }
        });
    }

    private Uri getSlotUri(int slot) {
        switch (slot) {
            case SLOT_AADHAAR_FRONT: return aadhaarFrontUri;
            case SLOT_AADHAAR_BACK: return aadhaarBackUri;
            case SLOT_AADHAAR_VIDEO: return aadhaarVideoUri;
            case SLOT_BILL_PHOTO: return billPhotoUri;
            case SLOT_BOX_PHOTO: return boxPhotoUri;
            case SLOT_BILL_VIDEO: return billVideoUri;
            default: return null;
        }
    }

    /** Tapping an already-captured slot previews it (photo preview dialog, or plays the
     * video), with the option to replace or remove it - previously tapping a filled slot
     * silently reopened the capture chooser with no way to actually view what was captured. */
    private void showSlotPreview(Uri uri, int slot, boolean isVideo) {
        if (isVideo) {
            try {
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                intent.setDataAndType(uri, "video/*");
                intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Video preview error", e);
                AppToast.error(this, "Could not play this video");
            }
            new AlertDialog.Builder(this)
                    .setTitle("Statement Video")
                    .setMessage("This video has already been added.")
                    .setPositiveButton("Replace", (d, w) -> showCaptureChooser(slot, true))
                    .setNegativeButton("Remove", (d, w) -> removeSlot(slot))
                    .setNeutralButton("Close", null)
                    .show();
            return;
        }
        View view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_slot_preview, null);
        ImageView iv = view.findViewById(R.id.ivSlotPreview);
        com.bumptech.glide.Glide.with(this).load(uri).into(iv);
        new AlertDialog.Builder(this)
                .setView(view)
                .setPositiveButton("Replace", (d, w) -> showCaptureChooser(slot, false))
                .setNegativeButton("Remove", (d, w) -> removeSlot(slot))
                .setNeutralButton("Close", null)
                .show();
    }

    private void removeSlot(int slot) {
        assignSlotUri(slot, null);
        Integer includeId = slotIncludeIds.get(slot);
        if (includeId != null) {
            View slotView = findViewById(includeId);
            if (slotView != null) slotView.findViewById(R.id.ivSlotCheck).setVisibility(View.GONE);
        }
    }

    private void showCaptureChooser(int slot, boolean isVideo) {
        String[] options = {isVideo ? "Record Video" : "Capture Photo", "Choose from Gallery"};
        new AlertDialog.Builder(this)
                .setTitle(isVideo ? "Add Video" : "Add Photo")
                .setItems(options, (d, which) -> {
                    activeCaptureSlot = slot;
                    if (which == 0) capture(isVideo); else pickFromGallery(isVideo);
                })
                .show();
    }

    private void capture(boolean isVideo) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            return;
        }
        try {
            File dir = new File(getCacheDir(), "media_capture");
            if (!dir.exists()) dir.mkdirs();
            String ext = isVideo ? ".mp4" : ".jpg";
            File file = File.createTempFile("capture_", ext, dir);
            pendingCaptureUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            android.content.Intent intent = new android.content.Intent(isVideo
                    ? MediaStore.ACTION_VIDEO_CAPTURE : MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCaptureUri);
            intent.addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent, isVideo ? REQ_CAPTURE_VIDEO : REQ_CAPTURE_PHOTO);
        } catch (IOException e) {
            Log.e(TAG, "Capture setup error", e);
            AppToast.error(this, "Unable to open camera");
        }
    }

    private void pickFromGallery(boolean isVideo) {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
        intent.setType(isVideo ? "video/*" : "image/*");
        startActivityForResult(intent, isVideo ? REQ_PICK_VIDEO : REQ_PICK_PHOTO);
    }

    private void assignSlotUri(int slot, Uri uri) {
        switch (slot) {
            case SLOT_AADHAAR_FRONT: aadhaarFrontUri = uri; break;
            case SLOT_AADHAAR_BACK: aadhaarBackUri = uri; break;
            case SLOT_AADHAAR_VIDEO: aadhaarVideoUri = uri; break;
            case SLOT_BILL_PHOTO: billPhotoUri = uri; break;
            case SLOT_BOX_PHOTO: boxPhotoUri = uri; break;
            case SLOT_BILL_VIDEO: billVideoUri = uri; break;
            default: break;
        }
        if (uri != null) {
            Integer includeId = slotIncludeIds.get(slot);
            if (includeId != null) markSlotDone(includeId);
        }
    }

    private void markSlotDone(int includeId) {
        View slotView = findViewById(includeId);
        if (slotView != null) {
            slotView.findViewById(R.id.ivSlotCheck).setVisibility(View.VISIBLE);
        }
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
            integrator.setPrompt("Position the IMEI barcode inside the frame");
            integrator.setBeepEnabled(true);
            integrator.setOrientationLocked(true);
            integrator.setCaptureActivity(PortraitCaptureActivity.class);
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

        if (requestCode == REQ_CAPTURE_PHOTO || requestCode == REQ_CAPTURE_VIDEO) {
            if (resultCode == RESULT_OK && pendingCaptureUri != null) {
                assignSlotUri(activeCaptureSlot, pendingCaptureUri);
                AppToast.success(this, "Added");
            }
            return;
        }
        if (requestCode == REQ_PICK_PHOTO || requestCode == REQ_PICK_VIDEO) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                assignSlotUri(activeCaptureSlot, data.getData());
                AppToast.success(this, "Added");
            }
            return;
        }

        try {
            IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
            if (result != null && result.getContents() != null && scanTargetField != null) {
                String scanned = result.getContents().replaceAll("[^0-9]", "");
                if (scanned.length() >= 14) {
                    scanTargetField.setText(scanned.length() > 15 ? scanned.substring(0, 15) : scanned);
                    AppToast.success(this, "IMEI scanned successfully");
                } else if (!scanned.isEmpty()) {
                    scanTargetField.setText(scanned);
                    AppToast.warning(this, "The barcode did not contain a complete IMEI, please check it");
                } else {
                    AppToast.show(this, "This does not look like an IMEI barcode");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Scan result parse error", e);
        }
    }

    // ================= AUTO-FETCH =================

    /** Once the 10-digit mobile number is complete, look up a returning customer (400ms debounce) */
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
                        double khata = c.has("khata_balance") ? c.get("khata_balance").getAsDouble() : 0;
                        String msg = "Returning Customer: " + str(c, "name")
                                + " - Total jobs: " + (c.has("total_jobs") ? c.get("total_jobs").getAsInt() : 0);
                        if (khata > 0) msg += "\nOutstanding balance: Rs. " + khata;
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
            if (etMobile.length() != 10) { etMobile.setError("Enter a 10-digit mobile number"); return false; }
            if (etName.length() == 0) { etName.setError("Enter the customer name"); return false; }
        } else if (step == 1) {
            if (etModel.length() == 0) { etModel.setError("Enter the device model"); return false; }
        } else if (step == 2) {
            if (etProblem.length() == 0) { etProblem.setError("Describe the problem"); return false; }
            if (etCost.length() > 0 && NumUtils.parseDouble(etCost.getText().toString()) <= 0) {
                etCost.setError("Enter a valid amount"); return false;
            }
        }
        return true;
    }

    private void buildSummary() {
        String s = etName.getText() + " (" + etMobile.getText() + ")"
                + "\n" + etModel.getText()
                + "\nProblem: " + etProblem.getText()
                + (etCost.length() > 0 ? "\nEstimate: Rs. " + etCost.getText() : "\nEstimate: Not specified")
                + (etAdvance.length() > 0 ? "  |  Advance: Rs. " + etAdvance.getText() : "")
                + (etDelivery.length() > 0 ? "\nDelivery: " + etDelivery.getText() : "");
        tvSummary.setText(s);
    }

    // ================= SAVE ("who is saving this job card" confirmation) =================

    private void showSaveConfirmDialog() {
        View view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_staff_spinner, null);
        TextView tvPrompt = view.findViewById(R.id.tvPrompt);
        Spinner spinner = view.findViewById(R.id.spStaff);
        tvPrompt.setText("Who is saving this job card?");

        btnNext.setEnabled(false);
        StaffPickerHelper.populate(this, spinner, new StaffPickerHelper.StaffLoadCallback() {
            @Override public void onLoaded() { btnNext.setEnabled(true); }
            @Override public void onError() { btnNext.setEnabled(true); }
        });

        new AlertDialog.Builder(this)
                .setTitle("Confirm Save")
                .setView(view)
                .setPositiveButton("Save Job Card", (d, w) -> {
                    long staffId = StaffPickerHelper.getSelectedStaffId(spinner);
                    saveJob(staffId);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveJob(long createdByStaffId) {
        btnNext.setEnabled(false);
        JsonObject body = new JsonObject();
        if (existingCustomerId > 0) {
            body.addProperty("customer_id", existingCustomerId);
        } else {
            JsonObject cd = new JsonObject();
            cd.addProperty("name", etName.getText().toString().trim());
            cd.addProperty("mobile", etMobile.getText().toString().trim());
            cd.addProperty("address", etAddress.getText().toString().trim());
            body.add("customer_data", cd);
        }
        body.addProperty("device_model", etModel.getText().toString().trim());
        body.addProperty("imei1", etImei1.getText().toString().trim());
        body.addProperty("imei2", etImei2.getText().toString().trim());
        body.addProperty("lock_type", currentLockType());
        body.addProperty("device_password", currentLockValue());
        body.addProperty("accessories", etAccessories.getText().toString().trim());
        body.addProperty("alternate_mobile", etAlternateMobile.getText().toString().trim());
        body.addProperty("problem", etProblem.getText().toString().trim());
        body.addProperty("estimated_cost", NumUtils.parseDouble(etCost.getText().toString()));
        body.addProperty("expected_delivery", etDelivery.getText().toString().trim());
        body.addProperty("aadhaar_number", etAadhaarNumber.getText().toString().trim());
        body.addProperty("bill_number", etBillNumber.getText().toString().trim());
        if (createdByStaffId > 0) {
            body.addProperty("created_by_staff_id", createdByStaffId);
        }

        ApiClient.get(this).createJob(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                if (res.isSuccessful() && res.body() != null) {
                    try {
                        JsonObject job = res.body();
                        long jobId = job.get("id").getAsLong();
                        String jobIdLabel = job.has("job_id") ? job.get("job_id").getAsString() : "";
                        uploadDocumentationThenAdvance(jobId, job, jobIdLabel);
                    } catch (Exception e) {
                        Log.e(TAG, "Save-job parse error", e);
                        btnNext.setEnabled(true);
                        AppToast.warning(NewJobCardActivity.this, "Job created, but some data could not be displayed");
                        finish();
                    }
                } else {
                    btnNext.setEnabled(true);
                    AppToast.error(NewJobCardActivity.this, "Could not save the job, please try again");
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                btnNext.setEnabled(true);
                AppToast.error(NewJobCardActivity.this, "Network error");
            }
        });
    }

    /** After the job is created, uploads any captured Aadhaar/Bill documentation, then
     * records the advance payment (if any), then finishes the screen. */
    private void uploadDocumentationThenAdvance(long jobId, JsonObject job, String jobIdLabel) {
        java.util.List<Object[]> uploads = new java.util.ArrayList<>();
        if (aadhaarFrontUri != null) uploads.add(new Object[]{aadhaarFrontUri, "AADHAAR_FRONT", false});
        if (aadhaarBackUri != null) uploads.add(new Object[]{aadhaarBackUri, "AADHAAR_BACK", false});
        if (aadhaarVideoUri != null) uploads.add(new Object[]{aadhaarVideoUri, "AADHAAR_STATEMENT", true});
        if (billPhotoUri != null) uploads.add(new Object[]{billPhotoUri, "BILL_PHOTO", false});
        if (boxPhotoUri != null) uploads.add(new Object[]{boxPhotoUri, "BOX_PHOTO", false});
        if (billVideoUri != null) uploads.add(new Object[]{billVideoUri, "BILL_STATEMENT", true});

        uploadNext(jobId, uploads, 0, () -> proceedAfterDocumentation(job, jobIdLabel));
    }

    private void uploadNext(long jobId, java.util.List<Object[]> uploads, int index, Runnable onDone) {
        if (index >= uploads.size()) { onDone.run(); return; }
        Object[] item = uploads.get(index);
        Uri uri = (Uri) item[0];
        String category = (String) item[1];
        boolean isVideo = (boolean) item[2];
        uploadSingleMedia(jobId, uri, category, isVideo, () -> uploadNext(jobId, uploads, index + 1, onDone));
    }

    private void uploadSingleMedia(long jobId, Uri uri, String category, boolean isVideo, Runnable onDone) {
        try {
            String mimeType = getContentResolver().getType(uri);
            if (mimeType == null) mimeType = isVideo ? "video/mp4" : "image/jpeg";
            java.io.InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) { onDone.run(); return; }
            byte[] bytes = readAllBytes(in);
            in.close();
            RequestBody fileBody = RequestBody.create(MediaType.parse(mimeType), bytes);
            String ext = isVideo ? "mp4" : "jpg";
            MultipartBody.Part part = MultipartBody.Part.createFormData("file",
                    "upload_" + System.currentTimeMillis() + "." + ext, fileBody);
            RequestBody caption = RequestBody.create(MediaType.parse("text/plain"), "");
            RequestBody categoryBody = RequestBody.create(MediaType.parse("text/plain"), category);
            ApiClient.get(this).uploadJobMediaWithCategory(jobId, caption, categoryBody, part)
                    .enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                    onDone.run();
                }
                @Override
                public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                    onDone.run();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Documentation upload error", e);
            onDone.run();
        }
    }

    private byte[] readAllBytes(java.io.InputStream in) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int read;
        while ((read = in.read(data, 0, data.length)) != -1) buffer.write(data, 0, read);
        return buffer.toByteArray();
    }

    private void proceedAfterDocumentation(JsonObject job, String jobIdLabel) {
        double adv = NumUtils.parseDouble(etAdvance.getText().toString());
        if (adv > 0) {
            recordAdvance(job, adv, jobIdLabel);
        } else {
            done(jobIdLabel);
        }
    }

    private void recordAdvance(JsonObject job, double amount, String jobIdLabel) {
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
                    done(jobIdLabel);
                }
                @Override
                public void onFailure(@NonNull Call<JsonObject> c, @NonNull Throwable t) {
                    done(jobIdLabel);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Record-advance error", e);
            done(jobIdLabel);
        }
    }

    private void done(String jobId) {
        AppToast.success(this, "Job card created: " + jobId);
        finish();
    }
}
