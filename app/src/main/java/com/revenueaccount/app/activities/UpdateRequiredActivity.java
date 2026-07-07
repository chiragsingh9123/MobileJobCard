package com.revenueaccount.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.revenueaccount.app.R;

/** Blocking screen shown when the shop owner has enabled force-update and this
 * app build is older than the required minimum version. The user cannot proceed
 * into the app from here - the only action available is to open the Play Store. */
public class UpdateRequiredActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_required);
        String message = getIntent().getStringExtra("message");
        String storeUrl = getIntent().getStringExtra("store_url");
        ((TextView) findViewById(R.id.tvMessage)).setText(message);
        findViewById(R.id.btnUpdate).setOnClickListener(v -> {
            if (storeUrl != null && !storeUrl.isEmpty()) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(storeUrl)));
            }
        });
    }

    @Override
    public void onBackPressed() {
        // Intentionally blocked - the user must update before continuing
        moveTaskToBack(true);
    }
}
