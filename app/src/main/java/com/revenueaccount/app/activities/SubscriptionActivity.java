package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.razorpay.Checkout;
import com.razorpay.PaymentData;
import com.razorpay.PaymentResultWithDataListener;
import com.revenueaccount.app.R;
import com.revenueaccount.app.api.ApiClient;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Subscription: Razorpay-powered plan purchase (replaces the old manual
 * UPI-screenshot + admin-approval flow). Also supports voucher redemption
 * as a secondary path (unrelated to Razorpay - admin-issued promo codes).
 *
 * Flow: pick a plan -> we create a Razorpay order -> Razorpay Checkout opens
 * -> on success we verify the signature server-side -> subscription activates
 * immediately, no waiting on anyone to manually approve anything.
 */
public class SubscriptionActivity extends AppCompatActivity implements PaymentResultWithDataListener {

    private static final String TAG = "SubscriptionActivity";

    private LinearLayout planContainer, historyContainer, voucherSection;
    private View statusCard, processingOverlay, successOverlay, failureOverlay;
    private TextView tvStatusEyebrow, tvStatusHeadline, tvStatusSub, tvProcessingStatus,
            tvSuccessSub, tvFailureTitle, tvFailureSub;
    private SwipeRefreshLayout swipeRefresh;
    private long pendingPlanId = -1;
    private String pendingOrderId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subscription);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        Checkout.preload(getApplicationContext());

        statusCard = findViewById(R.id.statusCard);
        tvStatusEyebrow = findViewById(R.id.tvStatusEyebrow);
        tvStatusHeadline = findViewById(R.id.tvStatusHeadline);
        tvStatusSub = findViewById(R.id.tvStatusSub);
        planContainer = findViewById(R.id.planContainer);
        historyContainer = findViewById(R.id.historyContainer);
        voucherSection = findViewById(R.id.voucherSection);
        processingOverlay = findViewById(R.id.processingOverlay);
        successOverlay = findViewById(R.id.successOverlay);
        failureOverlay = findViewById(R.id.failureOverlay);
        tvProcessingStatus = findViewById(R.id.tvProcessingStatus);
        tvSuccessSub = findViewById(R.id.tvSuccessSub);
        tvFailureTitle = findViewById(R.id.tvFailureTitle);
        tvFailureSub = findViewById(R.id.tvFailureSub);

        findViewById(R.id.tvVoucherToggle).setOnClickListener(v ->
                voucherSection.setVisibility(voucherSection.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        findViewById(R.id.btnRedeem).setOnClickListener(v -> redeemVoucher());
        findViewById(R.id.btnSuccessDone).setOnClickListener(v -> {
            stopSuccessPulse();
            successOverlay.setVisibility(View.GONE);
            loadEverything();
        });
        findViewById(R.id.btnFailureClose).setOnClickListener(v -> failureOverlay.setVisibility(View.GONE));
        findViewById(R.id.btnFailureRetry).setOnClickListener(v -> {
            failureOverlay.setVisibility(View.GONE);
            if (pendingPlanId > 0) startPayment(pendingPlanId);
        });

        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeResources(R.color.primary);
        swipeRefresh.setOnRefreshListener(this::loadEverything);

        loadEverything();
    }

    private void loadEverything() {
        loadStatus();
        loadPlans();
        loadHistory();
    }

    // ================= STATUS CARD =================

    private void loadStatus() {
        ApiClient.get(this).subscriptionStatus().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                swipeRefresh.setRefreshing(false);
                if (!res.isSuccessful() || res.body() == null) return;
                try {
                    JsonObject body = res.body();
                    boolean isActive = body.get("is_active").getAsBoolean();
                    if (body.get("has_subscription").getAsBoolean()) {
                        JsonObject sub = body.getAsJsonObject("subscription");
                        String planName = sub.getAsJsonObject("plan").get("name").getAsString();
                        int days = sub.get("days_remaining").getAsInt();
                        if (isActive) {
                            renderStatus(true, "CURRENT PLAN", planName,
                                    days + (days == 1 ? " day remaining" : " days remaining"));
                        } else {
                            renderStatus(false, "SUBSCRIPTION EXPIRED", planName,
                                    "Renew now to keep using Mobile JobCard");
                        }
                    } else {
                        renderStatus(false, "NO ACTIVE PLAN", "Subscribe to get started",
                                "Choose a plan below to activate your shop");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Status render error", e);
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                swipeRefresh.setRefreshing(false);
            }
        });
    }

    private void renderStatus(boolean active, String eyebrow, String headline, String sub) {
        tvStatusEyebrow.setText(eyebrow);
        tvStatusHeadline.setText(headline);
        tvStatusSub.setText(sub);
        int tint = active ? R.color.success_light : R.color.error_light;
        int textColor = active ? R.color.success : R.color.error;
        statusCard.setBackgroundColor(getResources().getColor(tint));
        tvStatusEyebrow.setTextColor(getResources().getColor(textColor));
        tvStatusHeadline.setTextColor(getResources().getColor(R.color.text_primary));
        tvStatusSub.setTextColor(getResources().getColor(R.color.text_secondary));
    }

    // ================= PLANS =================

    private void loadPlans() {
        ApiClient.get(this).plans().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                if (!res.isSuccessful() || res.body() == null) {
                    AppToast.error(SubscriptionActivity.this,
                            "Could not load plans (server returned " + res.code() + ")");
                    Log.e(TAG, "Plans request failed: HTTP " + res.code());
                    return;
                }
                try {
                    planContainer.removeAllViews();
                    JsonArray plans = res.body().getAsJsonArray("results");
                    if (plans.size() == 0) {
                        TextView empty = new TextView(SubscriptionActivity.this);
                        empty.setText("No plans are available to purchase right now. "
                                + "Please contact support.");
                        empty.setTextColor(getResources().getColor(R.color.text_secondary));
                        empty.setTextSize(14f);
                        planContainer.addView(empty);
                        Log.w(TAG, "Plans endpoint returned an empty list");
                        return;
                    }
                    for (int i = 0; i < plans.size(); i++) {
                        addPlanCard(plans.get(i).getAsJsonObject(), i == plans.size() - 2);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Plans render error", e);
                    AppToast.error(SubscriptionActivity.this, "Something went wrong showing the plans");
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                AppToast.error(SubscriptionActivity.this, "Could not load plans - network error");
            }
        });
    }

    private void addPlanCard(JsonObject plan, boolean highlight) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_plan_card, planContainer, false);
        long id = jsonLong(plan, "id", -1);
        String name = jsonString(plan, "name", "Plan");
        double price = jsonDouble(plan, "price", 0);
        int days = (int) jsonLong(plan, "duration_days", 30);
        String period = days >= 365 ? "year" : "month";

        ((TextView) card.findViewById(R.id.tvPlanName)).setText(name);
        ((TextView) card.findViewById(R.id.tvPlanDesc)).setText(jsonString(plan, "description", ""));
        ((TextView) card.findViewById(R.id.tvPlanPrice)).setText(
                "Rs. " + (price == (long) price ? String.valueOf((long) price) : String.valueOf(price))
                        + " / " + period);
        if (highlight) card.findViewById(R.id.tvPlanBadge).setVisibility(View.VISIBLE);
        card.findViewById(R.id.btnSubscribe).setOnClickListener(v -> startPayment(id));
        planContainer.addView(card);
    }

    /** Plan data can come from a manually-inserted row (e.g. a test plan added
     * directly via SQL) that doesn't have every column filled in - these
     * helpers fall back to a sane default instead of crashing on a JSON null. */
    private String jsonString(JsonObject o, String key, String fallback) {
        return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString() : fallback;
    }

    private double jsonDouble(JsonObject o, String key, double fallback) {
        return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsDouble() : fallback;
    }

    private long jsonLong(JsonObject o, String key, long fallback) {
        return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsLong() : fallback;
    }

    // ================= RAZORPAY PAYMENT FLOW =================

    private void startPayment(long planId) {
        pendingPlanId = planId;
        showProcessing("Setting up payment...");
        JsonObject body = new JsonObject();
        body.addProperty("plan_id", planId);
        ApiClient.get(this).createSubscriptionOrder(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                if (!res.isSuccessful() || res.body() == null) {
                    hideProcessing();
                    showFailure("Couldn't Start Payment", extractError(res,
                            "The payment could not be set up. Please try again."));
                    return;
                }
                try {
                    JsonObject r = res.body();
                    pendingOrderId = r.get("order_id").getAsString();
                    hideProcessing();
                    openRazorpayCheckout(r);
                } catch (Exception e) {
                    hideProcessing();
                    Log.e(TAG, "Create order parse error", e);
                    showFailure("Couldn't Start Payment",
                            "Something went wrong setting up the payment. Please try again.");
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                hideProcessing();
                showFailure("Couldn't Start Payment", "A network error occurred. Please check your connection and try again.");
            }
        });
    }

    private void openRazorpayCheckout(JsonObject order) {
        try {
            Checkout checkout = new Checkout();
            checkout.setKeyID(order.get("razorpay_key_id").getAsString());
            JSONObject options = new JSONObject();
            options.put("name", "Mobile JobCard");
            options.put("description", order.getAsJsonObject("plan").get("name").getAsString() + " Plan");
            options.put("order_id", order.get("order_id").getAsString());
            options.put("currency", order.get("currency").getAsString());
            options.put("amount", order.get("amount_paise").getAsLong());
            JSONObject prefill = new JSONObject();
            prefill.put("contact", order.get("prefill_contact").getAsString());
            options.put("prefill", prefill);
            JSONObject retry = new JSONObject();
            retry.put("enabled", true);
            options.put("retry", retry);
            checkout.open(this, options);
        } catch (Exception e) {
            Log.e(TAG, "Razorpay checkout open error", e);
            AppToast.error(this, "Could not open the payment screen");
        }
    }

    @Override
    public void onPaymentSuccess(String razorpayPaymentId, PaymentData paymentData) {
        showProcessing("Verifying payment...");
        JsonObject body = new JsonObject();
        body.addProperty("razorpay_order_id", paymentData.getOrderId());
        body.addProperty("razorpay_payment_id", razorpayPaymentId);
        body.addProperty("razorpay_signature", paymentData.getSignature());
        ApiClient.get(this).verifySubscriptionPayment(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                hideProcessing();
                if (res.isSuccessful() && res.body() != null) {
                    showSuccess(res.body());
                } else {
                    showFailure("Payment Could Not Be Verified",
                            extractError(res, "Something went wrong confirming this payment. "
                                    + "If money was deducted, it will be auto-refunded within a "
                                    + "few days, or contact support with your payment ID."));
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                hideProcessing();
                showFailure("Couldn't Confirm Payment",
                        "A network error occurred while confirming this payment. If money was "
                                + "deducted, check Payment History in a moment - it usually catches "
                                + "up automatically. Contact support if it doesn't appear.");
            }
        });
    }

    @Override
    public void onPaymentError(int code, String description, PaymentData paymentData) {
        // Razorpay's documented error codes: 0=NETWORK_ERROR, 1=INVALID_OPTIONS,
        // 2=PAYMENT_CANCELED, 3=TLS_ERROR, 4=INCOMPATIBLE_PLUGINS,
        // 100=UNKNOWN_ERROR. Using the raw code here (rather than a SDK
        // constant) since it can't be verified against the real .aar in this
        // build environment - check Razorpay's Android SDK docs if this ever
        // needs to branch on more than just "was it a user-cancellation".
        if (code == 2) {
            showFailure("Payment Cancelled", "You cancelled the payment before it completed. "
                    + "No money was deducted - you can try again whenever you're ready.");
        } else {
            showFailure("Payment Failed", "The payment could not be completed. Please try again.");
        }
    }

    // ================= PROCESSING / SUCCESS / FAILURE UI =================

    private void showProcessing(String status) {
        tvProcessingStatus.setText(status);
        processingOverlay.setVisibility(View.VISIBLE);
    }

    private void hideProcessing() {
        processingOverlay.setVisibility(View.GONE);
    }

    /** One screen for all three failure cases (user cancelled, payment
     * failed, verification didn't succeed) - the title/message make clear
     * which one happened. "Try Again" re-starts checkout for the same plan. */
    private void showFailure(String title, String message) {
        try {
            tvFailureTitle.setText(title);
            tvFailureSub.setText(message);
            failureOverlay.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            // Even the failure screen itself should never crash the app -
            // fall back to a toast so the person at least sees something.
            Log.e(TAG, "Failure screen render error", e);
            AppToast.error(this, title + ": " + message);
        }
    }

    /** A short scale+fade entrance for the checkmark - smooth, not gimmicky,
     * respects the system's reduced-motion setting automatically since
     * ObjectAnimator durations are scaled by the OS when that's enabled. */
    private void showSuccess(JsonObject verifyResponse) {
        successOverlay.setVisibility(View.VISIBLE);
        try {
            String message = verifyResponse.has("message")
                    ? verifyResponse.get("message").getAsString() : "Your plan is now active.";
            tvSuccessSub.setText(message);
        } catch (Exception e) {
            Log.e(TAG, "Success message render error (payment itself was fine)", e);
        }

        // The overlay + text above are the important part - the payment is
        // already activated on the server by this point. The animation below
        // is a nice-to-have, so it's fully isolated: if it fails for any
        // reason (a stale/missing resource, say), the success screen still
        // shows correctly instead of a genuinely successful payment looking
        // like an error.
        try {
            View checkIcon = findViewById(R.id.ivSuccessCheck);
            if (checkIcon == null) {
                Log.w(TAG, "ivSuccessCheck not found - skipping animation, success screen still shows");
                return;
            }
            checkIcon.setScaleX(0f);
            checkIcon.setScaleY(0f);
            checkIcon.setAlpha(0f);

            ObjectAnimator scaleX = ObjectAnimator.ofFloat(checkIcon, "scaleX", 0f, 1f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(checkIcon, "scaleY", 0f, 1f);
            ObjectAnimator alpha = ObjectAnimator.ofFloat(checkIcon, "alpha", 0f, 1f);
            AnimatorSet entrance = new AnimatorSet();
            entrance.playTogether(scaleX, scaleY, alpha);
            entrance.setDuration(450);
            entrance.setInterpolator(new OvershootInterpolator(1.6f));
            entrance.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    startSuccessPulse(checkIcon);
                }
            });
            entrance.start();
        } catch (Exception e) {
            Log.e(TAG, "Success checkmark animation error (payment itself was fine)", e);
        }
    }

    /** Keeps the checkmark gently growing and shrinking (like most payment
     * apps' success screens) until the user taps Done - stored in a field so
     * it can be stopped cleanly instead of animating an already-hidden view. */
    private AnimatorSet successPulseAnimator;

    private void startSuccessPulse(View checkIcon) {
        ObjectAnimator pulseX = ObjectAnimator.ofFloat(checkIcon, "scaleX", 1f, 1.15f, 1f);
        ObjectAnimator pulseY = ObjectAnimator.ofFloat(checkIcon, "scaleY", 1f, 1.15f, 1f);
        successPulseAnimator = new AnimatorSet();
        successPulseAnimator.playTogether(pulseX, pulseY);
        successPulseAnimator.setDuration(900);
        successPulseAnimator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        pulseX.setRepeatCount(ValueAnimator.INFINITE);
        pulseY.setRepeatCount(ValueAnimator.INFINITE);
        successPulseAnimator.start();
    }

    private void stopSuccessPulse() {
        if (successPulseAnimator != null) {
            successPulseAnimator.cancel();
            successPulseAnimator = null;
        }
    }

    // ================= VOUCHER (secondary path, unrelated to Razorpay) =================

    private void redeemVoucher() {
        EditText etCode = findViewById(R.id.etVoucherCode);
        Button btnRedeem = findViewById(R.id.btnRedeem);
        String code = etCode.getText().toString().trim().toUpperCase();
        if (code.isEmpty()) { etCode.setError("Enter a voucher code"); return; }

        btnRedeem.setEnabled(false);
        Map<String, String> body = new HashMap<>();
        body.put("code", code);
        ApiClient.get(this).redeemVoucher(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                btnRedeem.setEnabled(true);
                if (res.isSuccessful() && res.body() != null && res.body().has("message")) {
                    etCode.setText("");
                    showSuccess(res.body());
                } else {
                    AppToast.error(SubscriptionActivity.this, extractError(res, "This voucher is invalid or has already been used"));
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                btnRedeem.setEnabled(true);
                AppToast.error(SubscriptionActivity.this, "Network error");
            }
        });
    }

    // ================= PAYMENT HISTORY =================

    private void loadHistory() {
        ApiClient.get(this).myPayments().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                historyContainer.removeAllViews();
                if (!res.isSuccessful() || res.body() == null) return;
                try {
                    JsonArray results = res.body().getAsJsonArray("results");
                    if (results.size() == 0) {
                        TextView empty = new TextView(SubscriptionActivity.this);
                        empty.setText("No payments yet");
                        empty.setTextColor(getResources().getColor(R.color.text_secondary));
                        empty.setTextSize(13f);
                        historyContainer.addView(empty);
                        return;
                    }
                    for (int i = 0; i < results.size(); i++) {
                        addHistoryRow(results.get(i).getAsJsonObject());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "History render error", e);
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {}
        });
    }

    private void addHistoryRow(JsonObject p) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_payment_history, historyContainer, false);
        String planName = p.getAsJsonObject("plan").get("name").getAsString();
        double amount = p.get("amount").getAsDouble();
        String status = p.get("status").getAsString();
        String date = p.get("created_at").getAsString();

        ((TextView) row.findViewById(R.id.tvHistoryPlan)).setText(planName + " Plan");
        ((TextView) row.findViewById(R.id.tvHistoryDate)).setText(
                date.length() >= 10 ? date.substring(0, 10) : date);
        ((TextView) row.findViewById(R.id.tvHistoryAmount)).setText("Rs. " + amount);

        TextView tvStatus = row.findViewById(R.id.tvHistoryStatus);
        tvStatus.setText(status);
        int color = status.equals("PAID") ? R.color.success
                : status.equals("FAILED") ? R.color.error : R.color.warning;
        tvStatus.setTextColor(getResources().getColor(color));

        historyContainer.addView(row);
    }

    private String extractError(Response<JsonObject> res, String fallback) {
        try {
            if (res.errorBody() != null) {
                JsonObject err = com.google.gson.JsonParser.parseString(res.errorBody().string()).getAsJsonObject();
                if (err.has("detail")) return err.get("detail").getAsString();
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSuccessPulse();
    }
}