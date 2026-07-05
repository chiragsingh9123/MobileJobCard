package com.revenueaccount.app.adapters;

import android.content.Context;
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

public class StaffAdapter extends RecyclerView.Adapter<StaffAdapter.VH> {

    public interface OnStaffAction {
        void onMoreClick(JsonObject staff, View anchor);
    }

    private static final String TAG = "StaffAdapter";
    private final Context ctx;
    private final OnStaffAction listener;
    private JsonArray data = new JsonArray();

    public StaffAdapter(Context ctx, OnStaffAction listener) {
        this.ctx = ctx;
        this.listener = listener;
    }

    public void setData(JsonArray d) { data = d != null ? d : new JsonArray(); notifyDataSetChanged(); }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
        return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_staff, p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        try {
            JsonObject s = data.get(pos).getAsJsonObject();
            String name = str(s, "first_name") + " " + str(s, "last_name");
            h.name.setText(name.trim());
            h.avatar.setText(name.trim().isEmpty() ? "?" : name.trim().substring(0, 1).toUpperCase());
            String role = str(s, "role");
            h.designation.setText((str(s, "designation").isEmpty() ? role : str(s, "designation"))
            + " • " + str(s, "mobile"));
            int jobsAssigned = s.has("jobs_assigned") ? s.get("jobs_assigned").getAsInt() : 0;
            h.stats.setText(jobsAssigned + " jobs assigned");
            boolean active = !s.has("is_active") || s.get("is_active").getAsBoolean();
            h.status.setText(active ? "Active" : "Inactive");
            h.status.setTextColor(active ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828"));
            boolean isOwnerRole = "OWNER".equals(role);
            h.btnMore.setVisibility(isOwnerRole ? View.GONE : View.VISIBLE);
            h.btnMore.setOnClickListener(v -> { if (listener != null) listener.onMoreClick(s, v); });
        } catch (Exception e) {
            Log.e(TAG, "Bind error at " + pos, e);
        }
    }

    private String str(JsonObject o, String key) {
        return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString() : "";
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView avatar, name, designation, stats, status;
        View btnMore;
        VH(View v) {
            super(v);
            avatar = v.findViewById(R.id.tvAvatar);
            name = v.findViewById(R.id.tvName);
            designation = v.findViewById(R.id.tvDesignation);
            stats = v.findViewById(R.id.tvStats);
            status = v.findViewById(R.id.tvStatus);
            btnMore = v.findViewById(R.id.btnMore);
        }
    }
}
