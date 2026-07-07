package com.revenueaccount.app.activities;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.revenueaccount.app.R;

/**
 * Privacy Policy - a comprehensive template written for a self-hosted repair-shop
 * management app. Because each shop owner hosts their own backend, the shop owner
 * is the data controller for their customers' information. The contact details
 * referenced here are pulled from the Developer Info / Shop Profile screens and
 * should be reviewed alongside applicable local law (such as India's Digital
 * Personal Data Protection Act, 2023) before being treated as a final legal document.
 */
public class PrivacyPolicyActivity extends AppCompatActivity {

    private static final String POLICY_TEXT =
    "This Privacy Policy explains what information Mobile JobCard collects, how it " +
    "is used, and how it is protected. By using this application, you agree to the " +
    "practices described below.\n\n" +

    "1. Information We Collect\n\n" +
    "• Shop details: business name, address, city, and GST number\n" +
    "• Staff details: name, mobile number, and role/designation\n" +
    "• Customer details: name, mobile number, address, and email (optional)\n" +
    "• Device and job details: brand, model, IMEI, problem description, screen " +
    "lock information (PIN/password/pattern, collected solely for repair purposes), " +
    "photographs, and videos\n" +
    "• Identity and ownership documentation: Aadhaar number, bill number, and " +
    "associated photos or statement videos, where provided by the customer\n" +
    "• Payment records and khata (credit ledger) balances\n" +
    "• Mobile number used for OTP verification during registration and login\n\n" +

    "2. How We Use This Information\n\n" +
    "Information is used to create and track job cards, communicate job status to " +
    "customers, manage staff accounts and permissions, record payments and " +
    "outstanding balances, and maintain a documented record of device handovers " +
    "for transparency and dispute resolution.\n\n" +

    "3. Where Your Data Is Stored\n\n" +
    "This application is self-hosted: all data is stored on the shop owner's own " +
    "server and database, not on a third-party cloud platform operated by us. " +
    "The security of that server - including backups and access control - is the " +
    "responsibility of the shop owner operating it.\n\n" +

    "4. Third-Party Services\n\n" +
    "• OTP delivery currently uses the Telegram Bot API as an interim arrangement " +
    "while SMS-based delivery is finalized; your mobile number and OTP code are " +
    "sent through this service for verification purposes only.\n" +
    "• Notifications sent to customers use the messaging apps already installed " +
    "on the staff member's device (such as WhatsApp or SMS); we do not store or " +
    "track the content of these messages ourselves.\n\n" +

    "5. Screen Lock Information\n\n" +
    "If a customer provides their device's PIN, password, or pattern, this is " +
    "stored solely so the assigned technician can access the device to perform " +
    "the repair. It is not used for any other purpose and should be cleared once " +
    "the job is complete.\n\n" +

    "6. Identity Documentation (Aadhaar / Bill & Box Details)\n\n" +
    "Aadhaar numbers, bill numbers, and any associated photos or statement videos " +
    "are collected only where the customer chooses to provide them, to help the " +
    "shop maintain a transparent, verifiable record of device ownership. This " +
    "information is stored alongside the job card and is only accessible to " +
    "authorized shop staff.\n\n" +

    "7. Photos and Videos\n\n" +
    "Device photographs and videos (including damage records and condition proof) " +
    "are attached to the relevant job card and stored on the shop's server. " +
    "Deleting an item from within the app removes it from storage immediately.\n\n" +

    "8. Data Retention\n\n" +
    "Job, customer, and payment records are retained for as long as the shop " +
    "owner's account remains active, or as required to meet legal, accounting, or " +
    "regulatory obligations. Shop owners may delete customer or job records " +
    "directly, subject to any retention requirements under applicable law.\n\n" +

    "9. Your Rights\n\n" +
    "Customers may contact the shop directly to access, correct, or request " +
    "deletion of their personal information. Contact details for the shop and " +
    "the application developer are available on the Shop Profile and Developer " +
    "Info screens within this app.\n\n" +

    "10. Data Sharing\n\n" +
    "We do not sell or share your information with advertisers or unrelated " +
    "third parties. Data is only shared with the limited third-party services " +
    "described in Section 4, strictly to provide the functionality described " +
    "there.\n\n" +

    "11. Children's Privacy\n\n" +
    "This application is intended for business use by shop owners and staff and " +
    "is not directed at children. We do not knowingly collect personal " +
    "information from children.\n\n" +

    "12. Changes to This Policy\n\n" +
    "This policy may be updated from time to time to reflect changes in the " +
    "application or applicable law. Continued use of the application after an " +
    "update constitutes acceptance of the revised policy.\n\n" +

    "13. Contact Us\n\n" +
    "For questions about this policy or how your data is handled, please contact " +
    "the shop directly, or reach the application developer using the details on " +
    "the Developer Info screen.\n\n" +

    "This document is a starting template. Shop owners should have it reviewed by " +
    "a qualified professional and customized for their business before relying " +
    "on it as a final legal document.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_policy);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.tvLastUpdated)).setText("Last updated: July 2026");
        ((TextView) findViewById(R.id.tvPolicyBody)).setText(POLICY_TEXT);
    }
}
