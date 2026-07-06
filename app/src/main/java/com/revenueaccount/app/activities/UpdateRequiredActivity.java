package com.revenueaccount.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.revenueaccount.app.R;

public class UpdateRequiredActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_required);
        String message = getIntent().getStringExtra("message");
        String storeUrl = getIntent().getStringExtra("store_url");
        ((TextView) findViewById(R.id.tvMessage)).setText(message);
        findViewById(R.id.btnUpdate).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(storeUrl))));
    }

    @Override
    public void onBackPressed() {
        // Intentionally blocked - user must update to proceed
        moveTaskToBack(true);
    }
}