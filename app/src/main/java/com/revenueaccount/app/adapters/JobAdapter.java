package com.revenueaccount.app.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.revenueaccount.app.R;
import com.revenueaccount.app.activities.JobDetailsActivity;

public class JobAdapter extends RecyclerView.Adapter<JobAdapter.VH> {

    private static final String TAG = "JobAdapter";
    private final Context ctx;
    private JsonArray jobs = new JsonArray();

    public JobAdapter(Context ctx) { this.ctx = ctx; }

    public void setData(JsonArray data) {
        jobs = data != null ? data : new JsonArray();
        notifyDataSetChanged();
    }

    public static int statusColor(String status) {
        switch (status) {
            case "REPAIRING": return Color.parseColor("#FF9800");
            case "WAITING_PARTS": return Color.parseColor("#7B1FA2");
            case "READY": return Color.parseColor("#4CAF50");
            case "DELIVERED": return Color.parseColor("#00897B");
            case "RWR": return Color.parseColor("#F44336");
            default: return Color.parseColor("#1565C0");
        }
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_job, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        // Ek bhi malformed row RecyclerView ko crash na kare, isliye try-catch
        try {
            JsonObject j = jobs.get(pos).getAsJsonObject();
            JsonObject c = j.has("customer") ? j.getAsJsonObject("customer") : new JsonObject();
            String status = j.has("status") ? j.get("status").getAsString() : "RECEIVED";
            h.jobId.setText(j.has("job_id") ? j.get("job_id").getAsString() : "—");
            h.status.setText(status.replace("_", " "));
            h.status.setTextColor(statusColor(status));
            h.device.setText(str(j, "device_brand") + " " + str(j, "device_model"));
            h.customer.setText(str(c, "name") + " • " + str(c, "mobile"));
            h.problem.setText(str(j, "problem"));
            h.amount.setText("₹" + str(j, "estimated_cost"));
            double bal = j.has("balance_amount") ? j.get("balance_amount").getAsDouble() : 0;
            h.balance.setText(bal > 0 ? "Baaki: ₹" + bal : " Paid");
            h.balance.setTextColor(bal > 0 ? Color.parseColor("#F44336") : Color.parseColor("#4CAF50"));
            long id = j.has("id") ? j.get("id").getAsLong() : -1;
            h.itemView.setOnClickListener(v -> {
                if (id < 0) return;
                Intent i = new Intent(ctx, JobDetailsActivity.class);
                i.putExtra("job_pk", id);
                ctx.startActivity(i);
            });
        } catch (Exception e) {
            Log.e(TAG, "Bind error at position " + pos, e);
        }
    }

    private String str(JsonObject o, String key) {
        return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString() : "";
    }

    @Override
    public int getItemCount() { return jobs.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView jobId, status, device, customer, problem, amount, balance;
        VH(View v) {
            super(v);
            jobId = v.findViewById(R.id.tvJobId);
            status = v.findViewById(R.id.tvStatus);
            device = v.findViewById(R.id.tvDevice);
            customer = v.findViewById(R.id.tvCustomer);
            problem = v.findViewById(R.id.tvProblem);
            amount = v.findViewById(R.id.tvAmount);
            balance = v.findViewById(R.id.tvBalance);
        }
    }
}
