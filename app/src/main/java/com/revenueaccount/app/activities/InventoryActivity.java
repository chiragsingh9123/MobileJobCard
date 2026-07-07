package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.gson.JsonObject;
import com.revenueaccount.app.R;
import com.revenueaccount.app.adapters.ProductAdapter;
import com.revenueaccount.app.api.ApiClient;
import com.revenueaccount.app.utils.NumUtils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Screen 10: Inventory — static summary cards, category filter, search, add product/stock */
public class InventoryActivity extends AppCompatActivity {

    private static final String TAG = "InventoryActivity";
    private final String[][] CATS = {{"All", ""}, {"Display", "DISPLAY"}, {"Battery", "BATTERY"},
        {"IC", "IC"}, {"Connector", "CONNECTOR"}, {"Accessory", "ACCESSORY"}};

    private ProductAdapter adapter;
    private SwipeRefreshLayout swipe;
    private TextView tvEmpty;
    private String category = "", search = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        setStatLabel(R.id.statTotal, "Total");
        setStatLabel(R.id.statInStock, "In Stock");
        setStatLabel(R.id.statLow, "Low");
        setStatLabel(R.id.statOut, "Out");

        RecyclerView rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProductAdapter(this, this::load);
        rv.setAdapter(adapter);
        tvEmpty = findViewById(R.id.tvEmpty);

        ChipGroup chips = findViewById(R.id.chipGroup);
        for (int i = 0; i < CATS.length; i++) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_filter_chip, chips, false);
            chip.setText(CATS[i][0]);
            chip.setTag(CATS[i][1]);
            if (i == 0) chip.setChecked(true);
            chip.setOnClickListener(v -> { category = (String) v.getTag(); load(); });
            chips.addView(chip);
        }

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

        findViewById(R.id.fabAdd).setOnClickListener(v -> addProductDialog());

        swipe = findViewById(R.id.swipeRefresh);
        swipe.setColorSchemeResources(R.color.primary);
        swipe.setOnRefreshListener(this::load);
    }

    @Override
    protected void onResume() { super.onResume(); load(); }

    private void load() {
        swipe.setRefreshing(true);
        ApiClient.get(this).products(category, "", search).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                swipe.setRefreshing(false);
                if (res.isSuccessful() && res.body() != null) {
                    try {
                        adapter.setData(res.body().getAsJsonArray("results"));
                        tvEmpty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
                    } catch (Exception e) {
                        Log.e(TAG, "Product list render error", e);
                    }
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                swipe.setRefreshing(false);
                AppToast.show(InventoryActivity.this, "Network error");
            }
        });
        ApiClient.get(this).productsSummary().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                if (res.isSuccessful() && res.body() != null) {
                    try {
                        JsonObject s = res.body();
                        setStatValue(R.id.statTotal, safeStr(s, "total_products"), "#1565C0");
                        setStatValue(R.id.statInStock, safeStr(s, "in_stock"), "#4CAF50");
                        setStatValue(R.id.statLow, safeStr(s, "low_stock"), "#FF9800");
                        setStatValue(R.id.statOut, safeStr(s, "out_of_stock"), "#F44336");
                    } catch (Exception e) {
                        Log.e(TAG, "Summary render error", e);
                    }
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {}
        });
    }

    private void setStatLabel(int includeId, String label) {
        View card = findViewById(includeId);
        if (card == null) return;
        ((TextView) card.findViewById(R.id.statLabel)).setText(label);
    }

    private void setStatValue(int includeId, String value, String color) {
        View card = findViewById(includeId);
        if (card == null) return;
        TextView tv = card.findViewById(R.id.statValue);
        tv.setText(value);
        tv.setTextColor(Color.parseColor(color));
    }

    private String safeStr(JsonObject d, String key) {
        return (d.has(key) && !d.get(key).isJsonNull()) ? d.get(key).getAsString() : "0";
    }

    /** Naya product add dialog */
    private void addProductDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(48, 24, 48, 8);

        EditText etName = mkInput(form, "Product Name * (Vivo Y22 Display)", InputType.TYPE_CLASS_TEXT);
        Spinner spCat = new Spinner(this);
        String[] cats = {"DISPLAY", "BATTERY", "IC", "CONNECTOR", "ACCESSORY"};
        spCat.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, cats));
        form.addView(spCat);
        EditText etBrand = mkInput(form, "Brand", InputType.TYPE_CLASS_TEXT);
        EditText etPurchase = mkInput(form, "Purchase Price ₹ *", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText etSale = mkInput(form, "Sale Price ₹ *", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText etQty = mkInput(form, "Quantity *", InputType.TYPE_CLASS_NUMBER);

        ScrollView sc = new ScrollView(this);
        sc.addView(form);

        new AlertDialog.Builder(this)
        .setTitle(" Naya Product")
        .setView(sc)
        .setPositiveButton("Save", (d, w) -> {
            if (etName.length() == 0) {
                AppToast.show(this, "Enter the product name");
                return;
            }
            JsonObject b = new JsonObject();
            b.addProperty("name", etName.getText().toString().trim());
            b.addProperty("category", (String) spCat.getSelectedItem());
            b.addProperty("brand", etBrand.getText().toString().trim());
            b.addProperty("purchase_price", NumUtils.parseDouble(etPurchase.getText().toString()));
            b.addProperty("sale_price", NumUtils.parseDouble(etSale.getText().toString()));
            b.addProperty("quantity", NumUtils.parseInt(etQty.getText().toString()));
            ApiClient.get(this).createProduct(b).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(@NonNull Call<JsonObject> c, @NonNull Response<JsonObject> r) {
                    if (r.isSuccessful()) {
                        AppToast.show(InventoryActivity.this, " Product added");
                        load();
                    } else {
                        AppToast.show(InventoryActivity.this, "The product could not be saved");
                    }
                }
                @Override
                public void onFailure(@NonNull Call<JsonObject> c, @NonNull Throwable t) {
                    AppToast.show(InventoryActivity.this, "Network error");
                }
            });
        })
        .setNegativeButton("Cancel", null)
        .show();
    }

    private EditText mkInput(LinearLayout parent, String hint, int type) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setInputType(type);
        parent.addView(et);
        return et;
    }
}
