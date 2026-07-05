package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
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

/**
 * Screen 8: Delivery & Payment
 * KHATA FEATURE: Agar customer pura payment nahi karta,
 * to baaki amount automatically uske khate me chala jata hai.
 */
public class DeliveryActivity extends AppCompatActivity {

    private long jobPk, customerId;
    private double totalCost, paidAlready;

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
        ((TextView) findViewById(R.id.tvTotalCost)).setText("₹" + totalCost);
        ((TextView) findViewById(R.id.tvAdvance)).setText("₹" + paidAlready);
        double balance = totalCost - paidAlready;
        ((TextView) findViewById(R.id.tvBalance)).setText("₹" + balance);

        EditText etReceived = findViewById(R.id.etReceivedAmount);
        TextView tvKhataWarning = findViewById(R.id.tvKhataWarning);
        etReceived.setText(balance > 0 ? String.valueOf(balance) : "0");

        etReceived.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                double received = com.revenueaccount.app.utils.NumUtils.parseDouble(s.toString());
                double remaining = balance - received;
                if (remaining > 0) {
                    tvKhataWarning.setText(" ₹" + remaining
                    + " KHATE me jayega — customer agli baar dega");
                    tvKhataWarning.setVisibility(TextView.VISIBLE);
                } else {
                    tvKhataWarning.setVisibility(TextView.GONE);
                }
            }
        });

        Button btnComplete = findViewById(R.id.btnCompleteDelivery);
        RadioGroup rgMethod = findViewById(R.id.rgMethod);

        btnComplete.setOnClickListener(v -> {
            double received;
            try { received = Double.parseDouble(etReceived.getText().toString()); }
            catch (NumberFormatException e) { received = 0; }

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
                        markDelivered(btnComplete);
                    }
                    @Override
                    public void onFailure(@NonNull Call<JsonObject> c, @NonNull Throwable t) {
                        btnComplete.setEnabled(true);
                        AppToast.show(DeliveryActivity.this, "Payment save nahi hua");
                    }
                });
            } else {
                markDelivered(btnComplete);
            }
        });
    }

    private void markDelivered(Button btn) {
        JsonObject body = new JsonObject();
        body.addProperty("status", "DELIVERED");
        ApiClient.get(this).updateJobStatus(jobPk, body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                btn.setEnabled(true);
                if (res.isSuccessful() && res.body() != null) {
                    String msg = " Delivery complete!";
                    if (res.body().has("message")) msg += "\n" + res.body().get("message").getAsString();
                    AppToast.show(DeliveryActivity.this, msg);
                    setResult(RESULT_OK);
                    finish();
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                btn.setEnabled(true);
                AppToast.show(DeliveryActivity.this, "Error: " + t.getMessage());
            }
        });
    }
}
