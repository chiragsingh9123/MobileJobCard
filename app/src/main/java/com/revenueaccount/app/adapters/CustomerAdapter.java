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
import com.revenueaccount.app.activities.CustomerDetailActivity;

public class CustomerAdapter extends RecyclerView.Adapter<CustomerAdapter.VH> {

    private static final String TAG = "CustomerAdapter";
    private final Context ctx;
    private JsonArray data = new JsonArray();

    public CustomerAdapter(Context ctx) { this.ctx = ctx; }

    public void setData(JsonArray d) { data = d != null ? d : new JsonArray(); notifyDataSetChanged(); }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_customer, p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        try {
            JsonObject c = data.get(pos).getAsJsonObject();
            String name = c.has("name") && !c.get("name").isJsonNull() ? c.get("name").getAsString() : "?";
            h.name.setText(name);
            h.avatar.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());
            h.mobile.setText(c.has("mobile") ? c.get("mobile").getAsString() : "");
            h.type.setText(c.has("customer_type") ? c.get("customer_type").getAsString() : "NORMAL");
            h.stats.setText("Total Jobs: " + (c.has("total_jobs") ? c.get("total_jobs").getAsInt() : 0));
            double khata = c.has("khata_balance") ? c.get("khata_balance").getAsDouble() : 0;
            if (khata > 0) {
                h.khata.setText("Baaki\n₹" + khata);
                h.khata.setTextColor(Color.parseColor("#BB4B4B"));
            } else {
                h.khata.setText(" Clear");
                h.khata.setTextColor(Color.parseColor("#357A54"));
            }
            long id = c.has("id") ? c.get("id").getAsLong() : -1;
            h.itemView.setOnClickListener(v -> {
                if (id < 0) return;
                Intent i = new Intent(ctx, CustomerDetailActivity.class);
                i.putExtra("customer_id", id);
                ctx.startActivity(i);
            });
        } catch (Exception e) {
            Log.e(TAG, "Bind error at position " + pos, e);
        }
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView avatar, name, type, mobile, stats, khata;
        VH(View v) {
            super(v);
            avatar = v.findViewById(R.id.tvAvatar);
            name = v.findViewById(R.id.tvName);
            type = v.findViewById(R.id.tvType);
            mobile = v.findViewById(R.id.tvMobile);
            stats = v.findViewById(R.id.tvStats);
            khata = v.findViewById(R.id.tvKhata);
        }
    }
}
