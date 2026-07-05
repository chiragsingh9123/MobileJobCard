package com.revenueaccount.app.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import com.revenueaccount.app.R;
import com.revenueaccount.app.activities.DashboardActivity;

/** Local notifications — Dashboard load hote hi low-stock / khata-pending alert deta hai */
public class NotificationHelper {
    private static final String CHANNEL_ID = "revenue_account_alerts";

    public static void createChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
            "Mobile JobCard Alerts", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Low stock, khata pending, aur subscription alerts");
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    public static void show(Context ctx, int id, String title, String message) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        && ActivityCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED) {
            return; // permission nahi hai to silently skip — crash nahi karna
        }
        Intent intent = new Intent(ctx, DashboardActivity.class);
        PendingIntent pi = PendingIntent.getActivity(ctx, id, intent,
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .setContentIntent(pi);
        try {
            androidx.core.app.NotificationManagerCompat.from(ctx).notify(id, builder.build());
        } catch (SecurityException ignored) {
            // permission revoke ho sakti hai — crash na ho
        }
    }
}
