package com.revenueaccount.app.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;
import com.google.android.material.snackbar.Snackbar;

/**
 * In-app "toast" — traditional Android Toast use NAHI karta (jaisa maanga gaya).
 * Iski jagah Material Snackbar use hota hai jo activity ke andar hi (root content view par)
 * anchor hoti hai, aur zyada professional lagti hai. Activity ya Adapter — dono se
 * (Context ke through) call kiya ja sakta hai.
 */
public class AppToast {

    public enum Type { INFO, SUCCESS, ERROR, WARNING }

    public static void show(Context context, String message) {
        show(context, message, Type.INFO);
    }

    public static void success(Context context, String message) {
        show(context, message, Type.SUCCESS);
    }

    public static void error(Context context, String message) {
        show(context, message, Type.ERROR);
    }

    public static void warning(Context context, String message) {
        show(context, message, Type.WARNING);
    }

    public static void show(Context context, String message, Type type) {
        if (context == null || !(context instanceof Activity)) return;
        Activity activity = (Activity) context;
        if (activity.isFinishing()) return;
        try {
            View root = activity.findViewById(android.R.id.content);
            Snackbar snackbar = Snackbar.make(root, message, Snackbar.LENGTH_LONG);
            View sbView = snackbar.getView();

            int bgColor;
            switch (type) {
                case SUCCESS: bgColor = Color.parseColor("#2E7D32"); break;
                case ERROR: bgColor = Color.parseColor("#C62828"); break;
                case WARNING: bgColor = Color.parseColor("#E65100"); break;
                default: bgColor = Color.parseColor("#323232");
            }
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(bgColor);
            bg.setCornerRadius(24f);
            sbView.setBackground(bg);

            TextView tv = sbView.findViewById(com.google.android.material.R.id.snackbar_text);
            if (tv != null) {
                tv.setTextColor(Color.WHITE);
                tv.setMaxLines(4);
            }
            sbView.setElevation(8f);

            View bottomNav = activity.findViewById(com.revenueaccount.app.R.id.bottomNav);
            if (bottomNav != null) snackbar.setAnchorView(bottomNav);
            snackbar.show();
        } catch (Exception ignored) {
            // Snackbar dikhane me koi dikkat aaye to bhi app crash na ho
        }
    }
}
