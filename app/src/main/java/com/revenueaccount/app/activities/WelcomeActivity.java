package com.revenueaccount.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.revenueaccount.app.R;
import com.revenueaccount.app.utils.SessionManager;
import java.util.ArrayList;
import java.util.List;

/**
 * Screen shown once, before Login/Signup, on a device's very first launch
 * (tracked via SessionManager.hasSeenOnboarding()) - a short "Next, Next,
 * then Sign Up/Sign In" flow introducing the app, rather than dropping a
 * new user straight onto a login form with no context.
 */
public class WelcomeActivity extends AppCompatActivity {

    private static class Page {
        final int icon; final String title; final String desc;
        Page(int icon, String title, String desc) { this.icon = icon; this.title = title; this.desc = desc; }
    }

    private final List<Page> pages = new ArrayList<>();
    private ViewPager2 viewPager;
    private LinearLayout dotsContainer;
    private View authButtons;
    private androidx.appcompat.widget.AppCompatButton btnNext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        pages.add(new Page(R.drawable.ic_onboard_tracking, "Every Repair, Tracked",
                "From drop-off to delivery, know exactly who's handling every "
                        + "device and when - with a full timeline for each job."));
        pages.add(new Page(R.drawable.ic_onboard_khata, "Never Miss a Payment",
                "Khata tracking keeps every rupee a customer owes visible and "
                        + "up to date, automatically - no separate notebook needed."));
        pages.add(new Page(R.drawable.ic_onboard_docs, "Document With Confidence",
                "Aadhaar and bill photos, IMEI scanning, and staff "
                        + "accountability, all built in - for total transparency."));

        viewPager = findViewById(R.id.viewPager);
        dotsContainer = findViewById(R.id.dotsContainer);
        authButtons = findViewById(R.id.authButtons);
        btnNext = findViewById(R.id.btnNext);

        viewPager.setAdapter(new OnboardingAdapter());
        buildDots();

        findViewById(R.id.tvSkip).setOnClickListener(v -> goToLastPage());
        btnNext.setOnClickListener(v -> {
            int next = viewPager.getCurrentItem() + 1;
            if (next < pages.size()) viewPager.setCurrentItem(next);
        });
        findViewById(R.id.btnSignUp).setOnClickListener(v -> finishOnboarding(RegisterActivity.class));
        findViewById(R.id.btnSignIn).setOnClickListener(v -> finishOnboarding(LoginActivity.class));

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateDots(position);
                boolean isLast = position == pages.size() - 1;
                btnNext.setVisibility(isLast ? View.GONE : View.VISIBLE);
                authButtons.setVisibility(isLast ? View.VISIBLE : View.GONE);
                findViewById(R.id.tvSkip).setVisibility(isLast ? View.INVISIBLE : View.VISIBLE);
            }
        });
    }

    private void goToLastPage() {
        viewPager.setCurrentItem(pages.size() - 1);
    }

    private void finishOnboarding(Class<?> target) {
        new SessionManager(this).setOnboardingSeen();
        startActivity(new Intent(this, target));
        finish();
    }

    private void buildDots() {
        dotsContainer.removeAllViews();
        for (int i = 0; i < pages.size(); i++) {
            ImageView dot = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(5, 0, 5, 0);
            dot.setLayoutParams(lp);
            dot.setImageResource(i == 0 ? R.drawable.dot_active : R.drawable.dot_inactive);
            dotsContainer.addView(dot);
        }
    }

    private void updateDots(int activePosition) {
        for (int i = 0; i < dotsContainer.getChildCount(); i++) {
            ((ImageView) dotsContainer.getChildAt(i))
                    .setImageResource(i == activePosition ? R.drawable.dot_active : R.drawable.dot_inactive);
        }
    }

    @Override
    public void onBackPressed() {
        if (viewPager.getCurrentItem() > 0) {
            viewPager.setCurrentItem(viewPager.getCurrentItem() - 1);
        } else {
            super.onBackPressed();
        }
    }

    private class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_onboarding_page, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Page page = pages.get(position);
            holder.icon.setImageResource(page.icon);
            holder.title.setText(page.title);
            holder.desc.setText(page.desc);
        }

        @Override
        public int getItemCount() { return pages.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView icon; TextView title, desc;
            VH(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.ivOnboardIcon);
                title = itemView.findViewById(R.id.tvOnboardTitle);
                desc = itemView.findViewById(R.id.tvOnboardDesc);
            }
        }
    }
}