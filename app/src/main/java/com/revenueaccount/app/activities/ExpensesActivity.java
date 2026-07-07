package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.revenueaccount.app.R;
import com.revenueaccount.app.api.ApiClient;
import com.revenueaccount.app.utils.NumUtils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Expenses - record shop running costs, inventory purchases, and other business
 * expenses. Helps track profit accurately alongside job revenue. */
public class ExpensesActivity extends AppCompatActivity {

    private static final String TAG = "ExpensesActivity";
    private static final String[] CATEGORIES = {"RENT", "SALARY", "PARTS", "ELECTRICITY", "OTHER"};

    private final ExpenseAdapter adapter = new ExpenseAdapter();
    private SwipeRefreshLayout swipe;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expenses);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        swipe = findViewById(R.id.swipeRefresh);
        swipe.setColorSchemeResources(R.color.primary);
        swipe.setOnRefreshListener(this::load);

        findViewById(R.id.fabAdd).setOnClickListener(v -> showAddExpenseDialog());
    }

    @Override
    protected void onResume() { super.onResume(); load(); }

    private void load() {
        swipe.setRefreshing(true);
        ApiClient.get(this).listExpenses().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                swipe.setRefreshing(false);
                if (!res.isSuccessful() || res.body() == null) return;
                try {
                    JsonArray results = res.body().getAsJsonArray("results");
                    adapter.setData(results);
                    findViewById(R.id.tvEmpty).setVisibility(results.size() == 0 ? View.VISIBLE : View.GONE);
                    double total = 0;
                    for (int i = 0; i < results.size(); i++) {
                        total += results.get(i).getAsJsonObject().get("amount").getAsDouble();
                    }
                    ((TextView) findViewById(R.id.tvTotalExpenses)).setText("Rs. " + total);
                } catch (Exception e) {
                    Log.e(TAG, "Render error", e);
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                swipe.setRefreshing(false);
                AppToast.error(ExpensesActivity.this, "Network error");
            }
        });
    }

    private void showAddExpenseDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(48, 24, 48, 8);

        EditText etTitle = new EditText(this);
        etTitle.setHint("Expense title (e.g. Shop rent)");
        form.addView(etTitle);

        EditText etAmount = new EditText(this);
        etAmount.setHint("Amount (Rs.)");
        etAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        form.addView(etAmount);

        Spinner spCategory = new Spinner(this);
        spCategory.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, CATEGORIES));
        form.addView(spCategory);

        new AlertDialog.Builder(this)
                .setTitle("Add Expense")
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    if (etTitle.length() == 0) {
                        AppToast.warning(this, "Enter an expense title");
                        return;
                    }
                    double amount = NumUtils.parseDouble(etAmount.getText().toString());
                    if (amount <= 0) {
                        AppToast.warning(this, "Enter a valid amount");
                        return;
                    }
                    JsonObject body = new JsonObject();
                    body.addProperty("title", etTitle.getText().toString().trim());
                    body.addProperty("amount", amount);
                    body.addProperty("category", (String) spCategory.getSelectedItem());
                    ApiClient.get(this).createExpense(body).enqueue(new Callback<JsonObject>() {
                        @Override
                        public void onResponse(@NonNull Call<JsonObject> c, @NonNull Response<JsonObject> r) {
                            if (r.isSuccessful()) {
                                AppToast.success(ExpensesActivity.this, "Expense recorded");
                                load();
                            } else {
                                AppToast.error(ExpensesActivity.this, "Could not save the expense");
                            }
                        }
                        @Override
                        public void onFailure(@NonNull Call<JsonObject> c, @NonNull Throwable t) {
                            AppToast.error(ExpensesActivity.this, "Network error");
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    static class ExpenseAdapter extends RecyclerView.Adapter<ExpenseAdapter.VH> {
        private JsonArray data = new JsonArray();
        void setData(JsonArray d) { data = d != null ? d : new JsonArray(); notifyDataSetChanged(); }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup p, int v) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_expense, p, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            try {
                JsonObject e = data.get(pos).getAsJsonObject();
                h.title.setText(e.has("title") ? e.get("title").getAsString() : "");
                h.category.setText(e.has("category") ? e.get("category").getAsString() : "");
                h.amount.setText("Rs. " + (e.has("amount") ? e.get("amount").getAsDouble() : 0));
                String date = e.has("created_at") ? e.get("created_at").getAsString() : "";
                h.date.setText(date.length() >= 10 ? date.substring(0, 10) : date);
            } catch (Exception ex) {
                Log.e("ExpenseAdapter", "Bind error at " + pos, ex);
            }
        }

        @Override public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView title, category, amount, date;
            VH(View v) {
                super(v);
                title = v.findViewById(R.id.tvTitle);
                category = v.findViewById(R.id.tvCategory);
                amount = v.findViewById(R.id.tvAmount);
                date = v.findViewById(R.id.tvDate);
            }
        }
    }
}
