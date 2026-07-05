package com.revenueaccount.app.activities;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.revenueaccount.app.R;

/**
 * Privacy Policy — yeh ek REASONABLE TEMPLATE hai jo self-hosted repair-shop app ke
 * liye likha gaya hai. Shop owner (jo apna backend khud host karta hai) is app ka
 * "data controller" hota hai — isliye contact details neeche placeholder ke roop me
 * hain, inhe apni shop ki asli details se replace karein (Developer Info screen se
 * bhi jud sakta hai — dono ko apni jaankari ke saath customize karein).
 */
public class PrivacyPolicyActivity extends AppCompatActivity {

    private static final String POLICY_TEXT =
    "1. Hum Kya Data Collect Karte Hain\n\n" +
    "• Shop details: naam, address, city, GST number\n" +
    "• Staff details: naam, mobile number, role/designation\n" +
    "• Customer details: naam, mobile number, address, email (optional)\n" +
    "• Device/job details: brand, model, IMEI, problem description, screen " +
    "lock (PIN/pattern — sirf repair ke maqsad se), photos aur videos\n" +
    "• Payment aur khata (udhaar) records\n" +
    "• OTP verification ke liye mobile number (register/login ke waqt)\n\n" +

    "2. Data Kahan Store Hota Hai\n\n" +
    "Yeh app self-hosted hai — matlab saara data shop-owner ke apne server " +
    "(database) me store hota hai, kisi third-party cloud company ke paas nahi " +
    "jaata. Server ki security (backup, access control) shop owner ki " +
    "zimmedari hai.\n\n" +

    "3. Third-Party Services\n\n" +
    "• OTP verification ke liye Telegram Bot API use hoti hai — mobile number " +
    "aur OTP code Telegram ko bheja jaata hai (yeh temporary arrangement hai, " +
    "asli SMS gateway aane tak).\n" +
    "• Customer ko WhatsApp/SMS bhejne ke liye device ke installed apps (WhatsApp, " +
    "Messages) use hote hain — hum khud koi message store/track nahi karte.\n\n" +

    "4. Screen Lock (PIN/Pattern) Jaankari\n\n" +
    "Agar customer apne device ka PIN ya pattern deta hai, to yeh sirf repair " +
    "karne ke maqsad se store hota hai, taaki technician device open kar sake. " +
    "Isse kisi aur maqsad ke liye use nahi kiya jaata.\n\n" +

    "5. Photos & Videos\n\n" +
    "Device ki photo/video (damage-proof, condition record) sirf job card ke " +
    "saath attach hoti hai aur shop ke server par store hoti hai. Delete karne " +
    "par yeh turant hat jaati hai.\n\n" +

    "6. Aapke Rights\n\n" +
    "Customer apni jaankari dekhne, sudharne, ya delete karwane ke liye seedhe " +
    "shop se sampark kar sakta hai (neeche 'Developer Info' / Shop Profile me " +
    "contact details dekhein).\n\n" +

    "7. Data Kisi Aur Ko Bechte/Share Nahi Karte\n\n" +
    "Hum aapka data kisi advertiser ya third-party company ko nahi bechte ya " +
    "share nahi karte, sirf upar bataye gaye services (OTP, messaging) ke alawa.\n\n" +

    "This is a starting template. The shop owner should customize it for their " +
    "business and have it legally reviewed in line with applicable local law " +
    "(such as India's Digital Personal Data Protection Act) before finalizing it.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_policy);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.tvLastUpdated)).setText("Last updated: July 2026");
        ((TextView) findViewById(R.id.tvPolicyBody)).setText(POLICY_TEXT);
    }
}
