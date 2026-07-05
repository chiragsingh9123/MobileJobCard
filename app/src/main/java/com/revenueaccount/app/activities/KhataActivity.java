package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.content.Intent;
import android.os.Bundle;
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

/** Screen 8-related: KHATA — jin customers ka paisa baaki hai, unki list */
public class KhataActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipe;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_khata);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvKhata);
        rv.setLayoutManager(new LinearLayoutManager(this));
        tvEmpty = findViewById(R.id.tvEmpty);
        swipe = findViewById(R.id.swipeRefresh);
        swipe.setColorSchemeResources(R.color.primary);
        swipe.setOnRefreshListener(this::load);
    }

    @Override
    protected void onResume() { super.onResume(); load(); }

    private void load() {
        swipe.setRefreshing(true);
        ApiClient.get(this).khataPending().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                swipe.setRefreshing(false);
                if (res.isSuccessful() && res.body() != null) {
                    try {
                        JsonObject d = res.body();
                        ((TextView) findViewById(R.id.tvTotalPending))
                        .setText("Total Baaki: ₹" + d.get("total_pending").getAsString());
                        JsonArray results = d.getAsJsonArray("results");
                        ((RecyclerView) findViewById(R.id.rvKhata)).setAdapter(new KhataAdapter(results));
                        tvEmpty.setVisibility(results.size() == 0 ? View.VISIBLE : View.GONE);
                    } catch (Exception e) {
                        android.util.Log.e("KhataActivity", "Render error", e);
                    }
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                swipe.setRefreshing(false);
                AppToast.show(KhataActivity.this, "Network error");
            }
        });
    }

    class KhataAdapter extends RecyclerView.Adapter<KhataAdapter.VH> {
        private final JsonArray customers;
        KhataAdapter(JsonArray customers) { this.customers = customers; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_khata_customer, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            try {
                JsonObject c = customers.get(pos).getAsJsonObject();
                h.name.setText(c.has("name") ? c.get("name").getAsString() : "?");
                h.mobile.setText(c.has("mobile") ? c.get("mobile").getAsString() : "");
                h.amount.setText("₹" + (c.has("khata_balance") ? c.get("khata_balance").getAsString() : "0"));
                long id = c.has("id") ? c.get("id").getAsLong() : -1;
                h.itemView.setOnClickListener(v -> {
                    if (id < 0) return;
                    Intent i = new Intent(KhataActivity.this, CustomerDetailActivity.class);
                    i.putExtra("customer_id", id);
                    startActivity(i);
                });
            } catch (Exception e) {
                android.util.Log.e("KhataActivity", "Bind error at position " + pos, e);
            }
        }

        @Override public int getItemCount() { return customers.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView name, mobile, amount;
            VH(View v) {
                super(v);
                name = v.findViewById(R.id.tvName);
                mobile = v.findViewById(R.id.tvMobile);
                amount = v.findViewById(R.id.tvAmount);
            }
        }
    }
}
