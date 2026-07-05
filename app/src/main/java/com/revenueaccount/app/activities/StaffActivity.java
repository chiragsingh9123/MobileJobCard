package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.gson.JsonObject;
import com.revenueaccount.app.R;
import com.revenueaccount.app.adapters.StaffAdapter;
import com.revenueaccount.app.api.ApiClient;
import com.revenueaccount.app.utils.SessionManager;
import java.util.HashMap;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Staff Management — shop owner staff add/manage kar sakta hai, jobs assign kar sakta hai */
public class StaffActivity extends AppCompatActivity {

    private static final String TAG = "StaffActivity";
    private StaffAdapter adapter;
    private SwipeRefreshLayout swipe;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        boolean isOwner = new SessionManager(this).isOwner();
        View fab = findViewById(R.id.fabAdd);
        fab.setVisibility(isOwner ? View.VISIBLE : View.GONE);
        fab.setOnClickListener(v -> addStaffDialog());

        RecyclerView rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StaffAdapter(this, this::showStaffMenu);
        rv.setAdapter(adapter);

        swipe = findViewById(R.id.swipeRefresh);
        swipe.setColorSchemeResources(R.color.primary);
        swipe.setOnRefreshListener(this::load);
    }

    @Override
    protected void onResume() { super.onResume(); load(); }

    private void load() {
        swipe.setRefreshing(true);
        ApiClient.get(this).listStaff().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                swipe.setRefreshing(false);
                if (res.isSuccessful() && res.body() != null) {
                    try {
                        adapter.setData(res.body().getAsJsonArray("results"));
                        findViewById(R.id.tvEmpty).setVisibility(
                        adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
                    } catch (Exception e) {
                        Log.e(TAG, "Render error", e);
                    }
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                swipe.setRefreshing(false);
                AppToast.show(StaffActivity.this, "Network error");
            }
        });
    }

    private void showStaffMenu(JsonObject staff, View anchor) {
        if (!new SessionManager(this).isOwner()) return;
        PopupMenu menu = new PopupMenu(this, anchor);
        boolean active = !staff.has("is_active") || staff.get("is_active").getAsBoolean();
        menu.getMenu().add(active ? "Deactivate karein" : "Activate karein");
        menu.getMenu().add("Password reset karein");
        long staffId = staff.get("id").getAsLong();
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.startsWith("Deactivate") || title.startsWith("Activate")) {
                confirmToggleStaff(staffId, staff.get("first_name").getAsString(), active);
            } else {
                resetPasswordDialog(staffId, staff.get("first_name").getAsString());
            }
            return true;
        });
        menu.show();
    }

    private void confirmToggleStaff(long staffId, String name, boolean currentlyActive) {
        String action = currentlyActive ? "deactivate" : "activate";
        new AlertDialog.Builder(this)
        .setTitle(currentlyActive ? "Deactivate Staff Member" : "Activate Staff Member")
        .setMessage(currentlyActive
        ? name + " ko deactivate karne par unka app me login band ho jayega. Kya aap sure hain?"
        : name + " ko dobara activate karna chahte hain?")
        .setPositiveButton(currentlyActive ? "Deactivate Karein" : "Activate Karein",
        (d, w) -> toggleStaff(staffId))
        .setNegativeButton("Cancel", null)
        .show();
    }

    private void toggleStaff(long staffId) {
        ApiClient.get(this).toggleStaff(staffId).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                if (res.isSuccessful()) {
                    AppToast.show(StaffActivity.this, " Status update hua");
                    load();
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                AppToast.show(StaffActivity.this, "Network error");
            }
        });
    }

    private void resetPasswordDialog(long staffId, String name) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Naya password (kam se kam 4 characters)");
        new AlertDialog.Builder(this)
        .setTitle(name + " ka password reset karein")
        .setView(input)
        .setPositiveButton("Reset", (d, w) -> {
            String pw = input.getText().toString().trim();
            if (pw.length() < 4) {
                AppToast.show(this, "Kam se kam 4 characters daalein");
                return;
            }
            Map<String, String> body = new HashMap<>();
            body.put("password", pw);
            ApiClient.get(this).resetStaffPassword(staffId, body).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(@NonNull Call<JsonObject> c, @NonNull Response<JsonObject> r) {
                    AppToast.show(StaffActivity.this, " Password reset ho gaya");
                }
                @Override
                public void onFailure(@NonNull Call<JsonObject> c, @NonNull Throwable t) {
                    AppToast.show(StaffActivity.this, "Network error");
                }
            });
        })
        .setNegativeButton("Cancel", null)
        .show();
    }

    private void addStaffDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(48, 24, 48, 8);

        EditText etName = mkInput(form, "Naam *", InputType.TYPE_CLASS_TEXT);
        EditText etMobile = mkInput(form, "Mobile Number * (10 digit)", InputType.TYPE_CLASS_NUMBER);
        etMobile.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(10)});
        EditText etPassword = mkInput(form, "Password * (kam se kam 4 characters)",
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Spinner spDesignation = new Spinner(this);
        String[] designations = {"Technician", "Counter Staff", "Delivery Boy", "Manager"};
        spDesignation.setAdapter(new ArrayAdapter<>(this,
        android.R.layout.simple_spinner_dropdown_item, designations));
        form.addView(spDesignation);

        ScrollView sc = new ScrollView(this);
        sc.addView(form);

        new AlertDialog.Builder(this)
        .setTitle(" Naya Staff Member")
        .setView(sc)
        .setPositiveButton("Save", (d, w) -> {
            String name = etName.getText().toString().trim();
            String mobile = etMobile.getText().toString().trim();
            String password = etPassword.getText().toString();
            if (name.isEmpty()) { AppToast.show(this, "Naam daalein"); return; }
            if (mobile.length() != 10) { AppToast.show(this, "10 digit mobile daalein"); return; }
            if (password.length() < 4) { AppToast.show(this, "Password kam se kam 4 characters"); return; }

            Map<String, String> body = new HashMap<>();
            body.put("first_name", name);
            body.put("mobile", mobile);
            body.put("password", password);
            body.put("designation", (String) spDesignation.getSelectedItem());
            ApiClient.get(this).createStaff(body).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(@NonNull Call<JsonObject> c, @NonNull Response<JsonObject> r) {
                    if (r.isSuccessful()) {
                        AppToast.show(StaffActivity.this, " Staff add hua");
                        load();
                    } else {
                        AppToast.show(StaffActivity.this, "Save nahi hua — mobile pehle se registered ho sakta hai");
                    }
                }
                @Override
                public void onFailure(@NonNull Call<JsonObject> c, @NonNull Throwable t) {
                    AppToast.show(StaffActivity.this, "Network error");
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
