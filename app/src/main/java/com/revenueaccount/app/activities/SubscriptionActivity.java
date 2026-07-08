package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.revenueaccount.app.R;
import com.revenueaccount.app.api.ApiClient;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Subscription: Voucher Code redeem KARO ya UPI se pay karke screenshot+UTR submit karo.
 * Admin manually verify karke plan activate karta hai. */
public class SubscriptionActivity extends AppCompatActivity {

    private static final String TAG = "SubscriptionActivity";
    private static final int PICK_IMAGE_REQUEST = 701;

    private LinearLayout voucherSection, upiSection, planContainer, requestsContainer;
    private RadioGroup planRadioGroup;
    private long selectedPlanId = -1;
    private double selectedPlanPrice = 0;
    private Uri screenshotUri;
    private ImageView ivScreenshotPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subscription);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        voucherSection = findViewById(R.id.voucherSection);
        upiSection = findViewById(R.id.upiSection);
        planContainer = findViewById(R.id.planContainer);
        requestsContainer = findViewById(R.id.requestsContainer);
        ivScreenshotPreview = findViewById(R.id.ivScreenshotPreview);

        findViewById(R.id.btnTabVoucher).setOnClickListener(v -> switchTab(true));
        findViewById(R.id.btnTabUpi).setOnClickListener(v -> switchTab(false));

        findViewById(R.id.btnRedeem).setOnClickListener(v -> redeemVoucher());
        findViewById(R.id.btnPickScreenshot).setOnClickListener(v -> pickImage());
        findViewById(R.id.btnSubmitPayment).setOnClickListener(v -> submitPayment());

        loadCurrentPlan();
        loadPlans();
        loadUpiDetails();
        loadMyRequests();
    }

    private void switchTab(boolean voucher) {
        voucherSection.setVisibility(voucher ? View.VISIBLE : View.GONE);
        upiSection.setVisibility(voucher ? View.GONE : View.VISIBLE);
    }

    private void loadCurrentPlan() {
        TextView tv = findViewById(R.id.tvCurrentPlan);
        ApiClient.get(this).me().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                try {
                    if (res.isSuccessful() && res.body() != null
                    && res.body().has("subscription") && !res.body().get("subscription").isJsonNull()) {
                        JsonObject sub = res.body().getAsJsonObject("subscription");
                        String plan = sub.getAsJsonObject("plan").get("name").getAsString();
                        int days = sub.get("days_remaining").getAsInt();
                        tv.setText("Current Plan: " + plan + " - " + days + " days remaining");
                    } else {
                        tv.setText("No active plan - redeem a voucher or pay via UPI");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Plan render error", e);
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                tv.setText("Could not connect to the server");
            }
        });
    }

    private void redeemVoucher() {
        EditText etCode = findViewById(R.id.etVoucherCode);
        Button btnRedeem = findViewById(R.id.btnRedeem);
        String code = etCode.getText().toString().trim().toUpperCase();
        if (code.isEmpty()) { etCode.setError("Voucher code daalein"); return; }

        btnRedeem.setEnabled(false);
        Map<String, String> body = new HashMap<>();
        body.put("code", code);
        ApiClient.get(this).redeemVoucher(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                btnRedeem.setEnabled(true);
                if (res.isSuccessful() && res.body() != null && res.body().has("message")) {
                    AppToast.show(SubscriptionActivity.this, res.body().get("message").getAsString());
                    loadCurrentPlan();
                    etCode.setText("");
                } else {
                    AppToast.show(SubscriptionActivity.this, "This voucher is invalid or has already been used");
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                btnRedeem.setEnabled(true);
                AppToast.show(SubscriptionActivity.this, "Network error");
            }
        });
    }

    /** Plans ko RadioGroup me dikhao (RadioButton core Android widget hai, safe hai) */
    private void loadPlans() {
        ApiClient.get(this).plans().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                if (!res.isSuccessful() || res.body() == null) return;
                try {
                    planContainer.removeAllViews();
                    planRadioGroup = new RadioGroup(SubscriptionActivity.this);
                    planRadioGroup.setOrientation(RadioGroup.VERTICAL);
                    JsonArray plans = res.body().getAsJsonArray("results");
                    for (int i = 0; i < plans.size(); i++) {
                        JsonObject p = plans.get(i).getAsJsonObject();
                        RadioButton rb = new RadioButton(SubscriptionActivity.this);
                        rb.setText(p.get("name").getAsString() + " — ₹" + p.get("price").getAsDouble()
                        + " / " + p.get("duration_days").getAsInt() + " din");
                        rb.setTextSize(14f);
                        rb.setPadding(8, 16, 8, 16);
                        rb.setTag(new long[]{p.get("id").getAsLong()});
                        final double price = p.get("price").getAsDouble();
                        final long id = p.get("id").getAsLong();
                        rb.setOnClickListener(v -> {
                            selectedPlanId = id;
                            selectedPlanPrice = price;
                            findViewById(R.id.tvAmount).setVisibility(View.VISIBLE);
                            ((TextView) findViewById(R.id.tvAmount)).setText("₹" + price + " pay ");
                        });
                        planRadioGroup.addView(rb);
                        if (i == 0) rb.performClick();
                    }
                    planContainer.addView(planRadioGroup);
                } catch (Exception e) {
                    Log.e(TAG, "Plans render error", e);
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                AppToast.show(SubscriptionActivity.this, "Plans load nahi hue");
            }
        });
    }

    private void loadUpiDetails() {
        ApiClient.get(this).upiDetails().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                if (!res.isSuccessful() || res.body() == null) return;
                try {
                    JsonObject d = res.body();
                    String upiId = d.has("upi_id") ? d.get("upi_id").getAsString() : "";
                    ((TextView) findViewById(R.id.tvUpiId)).setText(
                    upiId.isEmpty() ? " Admin ne abhi UPI ID set nahi ki" : "UPI ID: " + upiId);
                    if (d.has("qr_image_url") && !d.get("qr_image_url").isJsonNull()) {
                        String url = ApiClient.BASE_URL.replaceAll("/$", "") + d.get("qr_image_url").getAsString();
                        com.bumptech.glide.Glide.with(SubscriptionActivity.this)
                        .load(url).into((ImageView) findViewById(R.id.ivQr));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "UPI details render error", e);
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {}
        });
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
         startActivityForResult(Intent.createChooser(intent, "Screenshot chunein"), PICK_IMAGE_REQUEST);
         }

         @Override
         protected void onActivityResult(int requestCode, int resultCode, Intent data) {
         super.onActivityResult(requestCode, resultCode, data);
         if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
         screenshotUri = data.getData();
         ivScreenshotPreview.setImageURI(screenshotUri);
         ivScreenshotPreview.setVisibility(View.VISIBLE);
         }
         }

         private void submitPayment() {
         EditText etUtr = findViewById(R.id.etUtr);
         String utr = etUtr.getText().toString().trim();

         if (selectedPlanId <= 0) {
         AppToast.show(this, "Pehle ek plan chunein");
         return;
         }
         if (utr.isEmpty()) {
         etUtr.setError("UTR / Transaction number daalein");
         return;
         }
         if (screenshotUri == null) {
         AppToast.show(this, "Payment screenshot chunein");
         return;
         }

         Button btnSubmit = findViewById(R.id.btnSubmitPayment);
         btnSubmit.setEnabled(false);
         try {
         InputStream is = getContentResolver().openInputStream(screenshotUri);
         ByteArrayOutputStream buffer = new ByteArrayOutputStream();
         byte[] data = new byte[4096];
         int n;
         while (is != null && (n = is.read(data)) != -1) buffer.write(data, 0, n);
         byte[] bytes = buffer.toByteArray();

         RequestBody planIdBody = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(selectedPlanId));
         RequestBody utrBody = RequestBody.create(MediaType.parse("text/plain"), utr);
         RequestBody imgBody = RequestBody.create(MediaType.parse("image/*"), bytes);
         MultipartBody.Part imgPart = MultipartBody.Part.createFormData("screenshot", "screenshot.jpg", imgBody);

         ApiClient.get(this).submitPayment(planIdBody, utrBody, imgPart).enqueue(new Callback<JsonObject>() {
         @Override
         public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
         btnSubmit.setEnabled(true);
         if (res.isSuccessful() && res.body() != null) {
         AppToast.show(SubscriptionActivity.this, "Submitted! The admin will verify it.");
         etUtr.setText("");
         screenshotUri = null;
         ivScreenshotPreview.setVisibility(View.GONE);
         loadMyRequests();
         } else {
         AppToast.show(SubscriptionActivity.this, "Submission failed, please try again");
         }
         }
         @Override
         public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
         btnSubmit.setEnabled(true);
         AppToast.show(SubscriptionActivity.this, "Network error: " + t.getMessage());
         }
         });
         } catch (Exception e) {
         btnSubmit.setEnabled(true);
         Log.e(TAG, "Submit payment error", e);
         AppToast.show(this, "Could not process the screenshot");
         }
         }

         private void loadMyRequests() {
         ApiClient.get(this).myPaymentRequests().enqueue(new Callback<JsonObject>() {
         @Override
         public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
         requestsContainer.removeAllViews();
         if (!res.isSuccessful() || res.body() == null) return;
         try {
         JsonArray results = res.body().getAsJsonArray("results");
         if (results.size() == 0) {
         addRequestRow("Koi payment request nahi hai", "#757575");
         return;
         }
         for (int i = 0; i < results.size(); i++) {
         JsonObject r = results.get(i).getAsJsonObject();
         String status = r.get("status").getAsString();
         String color = status.equals("APPROVED") ? "#2A6B45"
         : status.equals("REJECTED") ? "#A83E3E" : "#8F5416";
         String text = r.getAsJsonObject("plan").get("name").getAsString()
         + " — ₹" + r.get("amount").getAsDouble()
         + "\nUTR: " + r.get("utr_number").getAsString()
         + "\nStatus: " + status;
         addRequestRow(text, color);
         }
         } catch (Exception e) {
         Log.e(TAG, "Requests render error", e);
         }
         }
         @Override
         public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {}
         });
         }

         private void addRequestRow(String text, String color) {
         TextView tv = new TextView(this);
         tv.setText(text);
         tv.setTextSize(13f);
         tv.setTextColor(Color.parseColor(color));
         tv.setBackgroundColor(Color.WHITE);
         tv.setPadding(32, 24, 32, 24);
         LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
         LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
         lp.setMargins(0, 0, 0, 6);
         tv.setLayoutParams(lp);
         requestsContainer.addView(tv);
         }
         }
