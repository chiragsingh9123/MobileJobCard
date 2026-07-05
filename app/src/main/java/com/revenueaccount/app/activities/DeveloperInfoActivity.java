package com.revenueaccount.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.revenueaccount.app.R;


public class DeveloperInfoActivity extends AppCompatActivity {

    // Replace these with your actual information before publishing
    private static final String DEV_NAME = "Your Name / Company Name";
    private static final String DEV_TAGLINE = "YOur ";
    private static final String SUPPORT_EMAIL = "chiragsingh9123@gmail.com";
    private static final String SUPPORT_PHONE = "+917453842945";
    private static final String WEBSITE = "https://chiragsingh.online";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_developer_info);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        ((TextView) findViewById(R.id.tvDevName)).setText(DEV_NAME);
        ((TextView) findViewById(R.id.tvDevTagline)).setText(DEV_TAGLINE);
        ((TextView) findViewById(R.id.tvDevNote)).setText(
        "These contact details were set by this app's developer/support team. " +
        "For technical issues or support, please reach out using the options below.");

        LinearLayout container = findViewById(R.id.contactContainer);
        addRow(container, R.drawable.ic_email, "Email", SUPPORT_EMAIL, v -> {
            Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + SUPPORT_EMAIL));
            startActivity(i);
        });
        addDivider(container);
        addRow(container, R.drawable.ic_phone_call, "Phone", SUPPORT_PHONE, v ->
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + SUPPORT_PHONE))));
        addDivider(container);
        addRow(container, R.drawable.ic_globe, "Website", WEBSITE, v ->
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(WEBSITE))));
    }

    private void addRow(LinearLayout parent, int icon, String label, String value, View.OnClickListener onClick) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_menu_row, parent, false);
        ((ImageView) row.findViewById(R.id.ivIcon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.tvLabel)).setText(label + "\n" + value);
        row.setOnClickListener(onClick);
        parent.addView(row);
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 2));
        divider.setBackgroundColor(getColor(R.color.divider));
        parent.addView(divider);
    }
}
