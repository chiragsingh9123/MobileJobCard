package com.revenueaccount.app.activities;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.revenueaccount.app.R;

/** Roles & Permissions — informational screen + shortcut to Staff Management */
public class RolesInfoActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_roles_info);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnManageStaff).setOnClickListener(v ->
        startActivity(new Intent(this, StaffActivity.class)));
    }
}
