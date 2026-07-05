package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.gson.JsonObject;
import com.revenueaccount.app.R;
import com.revenueaccount.app.adapters.CustomerAdapter;
import com.revenueaccount.app.api.ApiClient;
import com.revenueaccount.app.utils.BottomNavHelper;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Screen 9: Customer Management (CRM) — search + khata filter */
public class CustomersActivity extends AppCompatActivity {

    private CustomerAdapter adapter;
    private SwipeRefreshLayout swipe;
    private TextView tvEmpty;
    private String search = "";
    private boolean pendingOnly = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customers);

        RecyclerView rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CustomerAdapter(this);
        rv.setAdapter(adapter);
        tvEmpty = findViewById(R.id.tvEmpty);

        findViewById(R.id.chipAll).setOnClickListener(v -> { pendingOnly = false; load(); });
        findViewById(R.id.chipPending).setOnClickListener(v -> { pendingOnly = true; load(); });

        EditText etSearch = findViewById(R.id.etSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {
                if (searchRunnable != null) handler.removeCallbacks(searchRunnable);
                searchRunnable = () -> { search = s.toString().trim(); load(); };
                handler.postDelayed(searchRunnable, 400);
            }
        });

        swipe = findViewById(R.id.swipeRefresh);
        swipe.setColorSchemeResources(R.color.primary);
        swipe.setOnRefreshListener(this::load);
        BottomNavHelper.setup(this, R.id.nav_customers);
    }

    @Override
    protected void onResume() { super.onResume(); load(); }

    private void load() {
        swipe.setRefreshing(true);
        ApiClient.get(this).customers(search, pendingOnly ? "true" : "")
        .enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                swipe.setRefreshing(false);
                if (res.isSuccessful() && res.body() != null) {
                    try {
                        adapter.setData(res.body().getAsJsonArray("results"));
                        tvEmpty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
                    } catch (Exception e) {
                        android.util.Log.e("CustomersActivity", "Render error", e);
                    }
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                swipe.setRefreshing(false);
                AppToast.show(CustomersActivity.this, "Network error");
            }
        });
    }
}
