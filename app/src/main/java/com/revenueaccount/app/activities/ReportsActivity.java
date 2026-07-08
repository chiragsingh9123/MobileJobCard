package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.revenueaccount.app.R;
import com.revenueaccount.app.api.ApiClient;
import com.revenueaccount.app.utils.BottomNavHelper;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Screen 11: Reports & Analytics — stats, daily revenue line chart, jobs pie chart */
public class ReportsActivity extends AppCompatActivity {

    private static final String TAG = "ReportsActivity";
    private final String[][] RANGES = {{"This Week", "7"}, {"This Month", "30"},
        {"3 Months", "90"}, {"This Year", "365"}};

    private LineChart lineChart;
    private PieChart pieChart;
    private LinearLayout pieLegend;
    private SwipeRefreshLayout swipe;
    private int days = 30;
    private boolean chartsReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        setStatLabel(R.id.statSales, "Total Sales");
        setStatLabel(R.id.statCollection, "Total Collection");
        setStatLabel(R.id.statExpense, "Total Expense");
        setStatLabel(R.id.statProfit, "Total Profit");

        lineChart = findViewById(R.id.lineChart);
        pieChart = findViewById(R.id.pieChart);
        pieLegend = findViewById(R.id.pieLegend);
        try {
            setupCharts();
            chartsReady = true;
        } catch (Exception e) {
            Log.e(TAG, "Chart setup failed", e);
        }

        ChipGroup chips = findViewById(R.id.chipGroup);
        for (int i = 0; i < RANGES.length; i++) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_filter_chip, chips, false);
            chip.setText(RANGES[i][0]);
            chip.setTag(RANGES[i][1]);
            if (i == 1) chip.setChecked(true);
            chip.setOnClickListener(v -> {
                try { days = Integer.parseInt((String) v.getTag()); } catch (Exception ignored) { days = 30; }
                load();
            });
            chips.addView(chip);
        }

        swipe = findViewById(R.id.swipeRefresh);
        swipe.setColorSchemeResources(R.color.primary);
        swipe.setOnRefreshListener(this::load);
        BottomNavHelper.setup(this, R.id.nav_reports);
    }

    @Override
    protected void onResume() { super.onResume(); load(); }

    private void setupCharts() {
        lineChart.getDescription().setEnabled(false);
        lineChart.getAxisRight().setEnabled(false);
        lineChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        lineChart.getXAxis().setGranularity(1f);
        lineChart.getLegend().setEnabled(false);
        lineChart.setNoDataText("Data load ho raha hai...");
        pieChart.getDescription().setEnabled(false);
        pieChart.getLegend().setEnabled(false);
        pieChart.setNoDataText("Data load ho raha hai...");
        pieChart.setHoleRadius(45f);
        pieChart.setTransparentCircleRadius(50f);
    }

    private void load() {
        swipe.setRefreshing(true);
        ApiClient.get(this).analytics(days).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                swipe.setRefreshing(false);
                if (res.isSuccessful() && res.body() != null) {
                    try {
                        render(res.body());
                    } catch (Exception e) {
                        Log.e(TAG, "Render error", e);
                        AppToast.show(ReportsActivity.this, "Data dikhane me dikkat aayi");
                    }
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                swipe.setRefreshing(false);
                AppToast.show(ReportsActivity.this, "Network error");
            }
        });
    }

    private void render(JsonObject d) {
        setStatValue(R.id.statSales, "₹" + safeStr(d, "total_sales"), "#2F6690");
        setStatValue(R.id.statCollection, "₹" + safeStr(d, "total_collection"), "#357A54");
        setStatValue(R.id.statExpense, "₹" + safeStr(d, "total_expense"), "#BB4B4B");
        setStatValue(R.id.statProfit, "₹" + safeStr(d, "profit"), "#7A5C93");

        if (!chartsReady) return;

        // Line chart: daily revenue
        JsonArray daily = d.has("daily_revenue") ? d.getAsJsonArray("daily_revenue") : new JsonArray();
        List<Entry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < daily.size(); i++) {
            JsonObject row = daily.get(i).getAsJsonObject();
            double amount = row.has("amount") ? row.get("amount").getAsDouble() : 0;
            entries.add(new Entry(i, (float) amount));
            String date = row.has("date") ? row.get("date").getAsString() : "";
            labels.add(date.length() >= 10 ? date.substring(8, 10) + "/" + date.substring(5, 7) : date);
        }
        if (entries.isEmpty()) entries.add(new Entry(0, 0));
        LineDataSet set = new LineDataSet(entries, "Revenue");
        set.setColor(Color.parseColor("#2F6690"));
        set.setLineWidth(2.5f);
        set.setCircleColor(Color.parseColor("#2F6690"));
        set.setCircleRadius(4f);
        set.setDrawFilled(true);
        set.setFillColor(Color.parseColor("#2F6690"));
        set.setFillAlpha(30);
        set.setValueTextSize(0f);
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        lineChart.setData(new LineData(set));
        lineChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        lineChart.getXAxis().setLabelCount(Math.min(6, Math.max(1, labels.size())));
        lineChart.animateX(600);
        lineChart.invalidate();

        // Pie chart: jobs by status
        JsonObject byStatus = d.has("jobs_by_status") ? d.getAsJsonObject("jobs_by_status") : new JsonObject();
        List<PieEntry> pieEntries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        pieLegend.removeAllViews();
        String[] order = {"RECEIVED", "REPAIRING", "WAITING_PARTS", "READY", "DELIVERED", "RWR"};
        String[] colorHex = {"#2F6690", "#A66418", "#7A5C93", "#357A54", "#2F7770", "#BB4B4B"};
        boolean any = false;
        for (int i = 0; i < order.length; i++) {
            if (byStatus.has(order[i])) {
                int count = byStatus.get(order[i]).getAsInt();
                if (count > 0) {
                    any = true;
                    pieEntries.add(new PieEntry(count, order[i]));
                    colors.add(Color.parseColor(colorHex[i]));
                    addLegend(order[i].replace("_", " "), colorHex[i], count);
                }
            }
        }
        if (!any) { pieEntries.add(new PieEntry(1, "No Data")); colors.add(Color.parseColor("#E0E0E0")); }
        PieDataSet pieSet = new PieDataSet(pieEntries, "");
        pieSet.setColors(colors);
        pieSet.setValueTextSize(0f);
        pieSet.setDrawValues(false);
        pieChart.setData(new PieData(pieSet));
        pieChart.animateY(600);
        pieChart.invalidate();
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

    private void addLegend(String label, String color, int count) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 6, 0, 6);
        TextView dot = new TextView(this);
        dot.setText("●");
        dot.setTextColor(Color.parseColor(color));
        dot.setTextSize(16f);
        TextView tv = new TextView(this);
        tv.setText(" " + label + ": " + count);
        tv.setTextSize(12f);
        tv.setTextColor(Color.parseColor("#212121"));
        row.addView(dot); row.addView(tv);
        pieLegend.addView(row);
    }
}
