package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.revenueaccount.app.R;
import com.revenueaccount.app.api.ApiClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Activity Log — poori shop ki har action ka audit trail (kisne kya kiya, kab) */
public class ActivityLogActivity extends AppCompatActivity {

    private static final String TAG = "ActivityLogActivity";
    private SwipeRefreshLayout swipe;
    private final LogAdapter adapter = new LogAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity_log);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        swipe = findViewById(R.id.swipeRefresh);
        swipe.setColorSchemeResources(R.color.primary);
        swipe.setOnRefreshListener(this::load);
    }

    @Override
    protected void onResume() { super.onResume(); load(); }

    private void load() {
        swipe.setRefreshing(true);
        ApiClient.get(this).shopActivityLog().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                swipe.setRefreshing(false);
                if (res.isSuccessful() && res.body() != null) {
                    try {
                        JsonArray results = res.body().getAsJsonArray("results");
                        adapter.setData(results);
                        findViewById(R.id.tvEmpty).setVisibility(
                        results.size() == 0 ? View.VISIBLE : View.GONE);
                    } catch (Exception e) {
                        Log.e(TAG, "Render error", e);
                    }
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                swipe.setRefreshing(false);
                AppToast.show(ActivityLogActivity.this, "Network error");
            }
        });
    }

    static class LogAdapter extends RecyclerView.Adapter<LogAdapter.VH> {
        private JsonArray data = new JsonArray();
        void setData(JsonArray d) { data = d != null ? d : new JsonArray(); notifyDataSetChanged(); }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_activity_log, p, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            try {
                JsonObject a = data.get(pos).getAsJsonObject();
                String action = a.has("action") ? a.get("action").getAsString() : "";
                String icon;
                switch (action) {
                    case "CREATED": icon = "🆕"; break;
                    case "ASSIGNED": icon = ""; break;
                    case "STATUS": icon = ""; break;
                    case "PAYMENT": icon = ""; break;
                    case "NOTE": icon = ""; break;
                    default: icon = "";
                }
                h.icon.setText(icon);
                String desc = a.has("description") ? a.get("description").getAsString() : "";
                String jobId = a.has("job_id") && !a.get("job_id").isJsonNull() ? a.get("job_id").getAsString() : null;
                h.desc.setText(jobId != null ? "[" + jobId + "] " + desc : desc);
                String time = a.has("created_at") ? a.get("created_at").getAsString() : "";
                String by = a.has("by_user") ? a.get("by_user").getAsString() : "System";
                h.meta.setText(" " + by + " • " + (time.length() >= 16 ? time.substring(0, 16).replace("T", " ") : time));
            } catch (Exception e) {
                Log.e("LogAdapter", "Bind error at " + pos, e);
            }
        }

        @Override public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView icon, desc, meta;
            VH(View v) {
                super(v);
                icon = v.findViewById(R.id.tvIcon);
                desc = v.findViewById(R.id.tvDescription);
                meta = v.findViewById(R.id.tvMeta);
            }
        }
    }
}
