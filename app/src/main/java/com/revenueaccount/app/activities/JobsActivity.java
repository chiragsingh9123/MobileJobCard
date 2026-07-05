package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.app.DatePickerDialog;
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
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.gson.JsonObject;
import com.revenueaccount.app.R;
import com.revenueaccount.app.adapters.JobAdapter;
import com.revenueaccount.app.api.ApiClient;
import com.revenueaccount.app.utils.BottomNavHelper;
import java.util.Calendar;
import java.util.Locale;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Screen 7: Repair Workspace — status filter chips + search + date filter + jobs list */
public class JobsActivity extends AppCompatActivity {

    private final String[][] FILTERS = {{"All", ""}, {"Received", "RECEIVED"},
        {"Repairing", "REPAIRING"}, {"Waiting Parts", "WAITING_PARTS"},
        {"Ready", "READY"}, {"Delivered", "DELIVERED"}, {"RWR", "RWR"}};

    private JobAdapter adapter;
    private SwipeRefreshLayout swipe;
    private TextView tvEmpty, tvDateFilterActive;
    private String currentStatus = "";
    private String currentSearch = "";
    private String currentDate = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_jobs);

        RecyclerView rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new JobAdapter(this);
        rv.setAdapter(adapter);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvDateFilterActive = findViewById(R.id.tvDateFilterActive);

        ChipGroup chips = findViewById(R.id.chipGroup);
        for (int i = 0; i < FILTERS.length; i++) {
            // Chip XML se inflate hota hai (naya Chip() seedha banane se
            // Material theme-enforcement crash aata hai) — yeh safe tareeka hai
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_filter_chip, chips, false);
            chip.setText(FILTERS[i][0]);
            chip.setTag(FILTERS[i][1]);
            if (i == 0) chip.setChecked(true);
            chip.setOnClickListener(v -> {
                currentStatus = (String) v.getTag();
                load();
            });
            chips.addView(chip);
        }

        EditText etSearch = findViewById(R.id.etSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {
                if (searchRunnable != null) handler.removeCallbacks(searchRunnable);
                searchRunnable = () -> { currentSearch = s.toString().trim(); load(); };
                handler.postDelayed(searchRunnable, 400);
            }
        });

        findViewById(R.id.btnDateFilter).setOnClickListener(v -> showDatePicker());
        tvDateFilterActive.setOnClickListener(v -> {
            currentDate = "";
            tvDateFilterActive.setVisibility(View.GONE);
            load();
        });

        swipe = findViewById(R.id.swipeRefresh);
        swipe.setColorSchemeResources(R.color.primary);
        swipe.setOnRefreshListener(this::load);

        BottomNavHelper.setup(this, R.id.nav_jobs);
    }

    /** Date se search — kisi din ke saare jobs dekhein */
    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            currentDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
            tvDateFilterActive.setText(" " + currentDate + " ");
            tvDateFilterActive.setVisibility(View.VISIBLE);
            load();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        swipe.setRefreshing(true);
        ApiClient.get(this).jobsFiltered(currentStatus, currentSearch, currentDate, "", "")
        .enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                swipe.setRefreshing(false);
                if (res.isSuccessful() && res.body() != null) {
                    try {
                        adapter.setData(res.body().getAsJsonArray("results"));
                        tvEmpty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
                    } catch (Exception e) {
                        android.util.Log.e("JobsActivity", "Render error", e);
                    }
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                swipe.setRefreshing(false);
                AppToast.error(JobsActivity.this, "Network error");
            }
        });
    }
}
