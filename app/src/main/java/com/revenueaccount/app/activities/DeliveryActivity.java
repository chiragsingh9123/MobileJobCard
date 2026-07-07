package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.JsonObject;
import com.revenueaccount.app.R;
import com.revenueaccount.app.api.ApiClient;
import com.revenueaccount.app.utils.NumUtils;
import com.revenueaccount.app.utils.StaffPickerHelper;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Screen 8: Delivery & Payment
 * KHATA FEATURE: Agar customer pura payment nahi karta, to baaki amount
 * automatically uske khate me chala jaata hai.
 * DISCOUNT: delivery ke waqt customer ko chhoot di ja sakti hai.
 * DELIVERED BY: "Set kaun deliver kar raha hai" popup se technician select hota hai.
 */
public class DeliveryActivity extends AppCompatActivity {

    private long jobPk, customerId;
    private double totalCost, paidAlready;
    private EditText etReceived, etDiscount;
    private TextView tvKhataWarning, tvBalance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delivery);

        jobPk = getIntent().getLongExtra("job_pk", -1);
        customerId = getIntent().getLongExtra("customer_id", -1);
        totalCost = getIntent().getDoubleExtra("total_cost", 0);
        paidAlready = getIntent().getDoubleExtra("paid_amount", 0);
        String jobId = getIntent().getStringExtra("job_id");

        ((TextView) findViewById(R.id.tvJobId)).setText(jobId);
        ((TextView) findViewById(R.id.tvTotalCost)).setText("Rs. " + totalCost);
        ((TextView) findViewById(R.id.tvAdvance)).setText("Rs. " + paidAlready);
        tvBalance = findViewById(R.id.tvBalance);

        etReceived = findViewById(R.id.etReceivedAmount);
        etDiscount = findViewById(R.id.etDiscount);
        tvKhataWarning = findViewById(R.id.tvKhataWarning);

        recomputeBalance();

        android.text.TextWatcher recompute = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) { updateKhataWarning(); }
        };
        etDiscount.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) { recomputeBalance(); }
        });
        etReceived.addTextChangedListener(recompute);

        Button btnComplete = findViewById(R.id.btnCompleteDelivery);
        btnComplete.setOnClickListener(v -> showDeliveredByDialog(btnComplete));
    }

    /** Discount badalne par balance aur suggested-received-amount dobara calculate karta hai */
    private void recomputeBalance() {
        double discount = NumUtils.parseDouble(etDiscount.getText().toString());
        double balance = (totalCost - discount) - paidAlready;
        tvBalance.setText("Rs. " + balance);
        etReceived.setText(balance > 0 ? String.valueOf(balance) : "0");
    }

    private void updateKhataWarning() {
        double discount = NumUtils.parseDouble(etDiscount.getText().toString());
        double balance = (totalCost - discount) - paidAlready;
        double received = NumUtils.parseDouble(etReceived.getText().toString());
        double remaining = balance - received;
        if (remaining > 0) {
            tvKhataWarning.setText("Rs. " + remaining + " will be added to the khata - to be collected next time");
            tvKhataWarning.setVisibility(TextView.VISIBLE);
        } else {
            tvKhataWarning.setVisibility(TextView.GONE);
        }
    }

    /** "Who is delivering this device?" - Delivery complete karne se pehle poochta hai */
    private void showDeliveredByDialog(Button btnComplete) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_staff_spinner, null);
        TextView tvPrompt = view.findViewById(R.id.tvPrompt);
        Spinner spinner = view.findViewById(R.id.spStaff);
        tvPrompt.setText("Who is delivering this device?");
        StaffPickerHelper.populate(this, spinner, null);

        new AlertDialog.Builder(this)
                .setTitle("Confirm Delivery")
                .setView(view)
                .setPositiveButton("Complete Delivery", (d, w) -> {
                    long staffId = StaffPickerHelper.getSelectedStaffId(spinner);
                    completeDelivery(btnComplete, staffId);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void completeDelivery(Button btnComplete, long deliveredByStaffId) {
        double received = NumUtils.parseDouble(etReceived.getText().toString());
        RadioGroup rgMethod = findViewById(R.id.rgMethod);
        String method = "CASH";
        int checked = rgMethod.getCheckedRadioButtonId();
        if (checked == R.id.rbUpi) method = "UPI";
        else if (checked == R.id.rbCard) method = "CARD";

        btnComplete.setEnabled(false);
        if (received > 0) {
            JsonObject pay = new JsonObject();
            pay.addProperty("job_card_id", jobPk);
            pay.addProperty("customer_id", customerId);
            pay.addProperty("amount", received);
            pay.addProperty("method", method);
            pay.addProperty("payment_type", "FINAL");
            ApiClient.get(this).createPayment(pay).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(@NonNull Call<JsonObject> c, @NonNull Response<JsonObject> r) {
                    markDelivered(btnComplete, deliveredByStaffId);
                }
                @Override
                public void onFailure(@NonNull Call<JsonObject> c, @NonNull Throwable t) {
                    btnComplete.setEnabled(true);
                    AppToast.error(DeliveryActivity.this, "The payment could not be saved");
                }
            });
        } else {
            markDelivered(btnComplete, deliveredByStaffId);
        }
    }

    private void markDelivered(Button btn, long deliveredByStaffId) {
        double discount = NumUtils.parseDouble(etDiscount.getText().toString());
        JsonObject body = new JsonObject();
        body.addProperty("status", "DELIVERED");
        body.addProperty("discount_amount", discount);
        if (deliveredByStaffId > 0) {
            body.addProperty("delivered_by_staff_id", deliveredByStaffId);
        }
        ApiClient.get(this).updateJobStatus(jobPk, body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                btn.setEnabled(true);
                if (res.isSuccessful() && res.body() != null) {
                    String msg = "Delivery complete!";
                    if (res.body().has("message")) msg += "\n" + res.body().get("message").getAsString();
                    AppToast.success(DeliveryActivity.this, msg);
                    setResult(RESULT_OK);
                    finish();
                } else {
                    AppToast.error(DeliveryActivity.this, "Delivery could not be completed");
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                btn.setEnabled(true);
                AppToast.error(DeliveryActivity.this, "Error: " + t.getMessage());
            }
        });
    }
}
