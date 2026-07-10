package com.revenueaccount.app.messaging;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.gson.JsonObject;
import com.revenueaccount.app.R;
import com.revenueaccount.app.activities.DashboardActivity;
import com.revenueaccount.app.activities.JobDetailsActivity;
import com.revenueaccount.app.api.ApiClient;
import com.revenueaccount.app.utils.SessionManager;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Receives push notifications sent from the backend and shows them as a
 * styled system notification - a distinct icon/color per notification type,
 * a large app icon, a "View Job" action for job-related pushes, and direct
 * deep-linking into the relevant job instead of always opening the Dashboard.
 * Also registers a fresh token with the backend whenever Firebase issues one.
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";
    public static final String CHANNEL_JOBS = "mobile_jobcard_jobs_v2";
    public static final String CHANNEL_ANNOUNCEMENTS = "mobile_jobcard_announcements_v2";
    private static final String GROUP_JOBS = "com.revenueaccount.app.JOB_UPDATES";
    private static final long[] VIBRATION_PATTERN = {0, 350, 150, 350};

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        syncTokenWithServer(this);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);
        // Backend sends data-only messages (see push_notifications.py) so this
        // method is always called, in foreground or background, and gets to
        // build the notification itself with the right icon/color/action.
        Map<String, String> data = message.getData();
        String title = data.getOrDefault("title", "Mobile JobCard");
        String body = data.getOrDefault("body", "");
        String type = data.getOrDefault("type", "");
        String jobId = data.getOrDefault("job_id", "");
        showNotification(title, body, type, jobId);
    }

    private void showNotification(String title, String body, String type, String jobId) {
        createChannelsIfNeeded();
        NotificationStyle style = styleFor(type);

        long jobPk = -1;
        try { if (!jobId.isEmpty()) jobPk = Long.parseLong(jobId); } catch (NumberFormatException ignored) {}

        Intent tapIntent = (jobPk > 0)
                ? new Intent(this, JobDetailsActivity.class).putExtra("job_pk", jobPk)
                : new Intent(this, DashboardActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        int requestCode = (int) System.currentTimeMillis();
        PendingIntent tapPending = PendingIntent.getActivity(this, requestCode, tapIntent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, style.channelId)
                .setSmallIcon(style.iconRes)
                .setColor(ContextCompat.getColor(this, R.color.primary))
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVibrate(VIBRATION_PATTERN)
                .setContentIntent(tapPending);

        if (jobPk > 0) {
            builder.setGroup(GROUP_JOBS);
            Intent viewJobIntent = new Intent(this, JobDetailsActivity.class).putExtra("job_pk", jobPk);
            viewJobIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent viewJobPending = PendingIntent.getActivity(
                    this, requestCode + 1, viewJobIntent, flags);
            builder.addAction(R.drawable.ic_receipt, "View Job", viewJobPending);
        }

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(requestCode, builder.build());
        }
    }

    /** Small holder for the icon+channel pairing that fits each push type. */
    private static class NotificationStyle {
        final int iconRes;
        final String channelId;
        NotificationStyle(int iconRes, String channelId) {
            this.iconRes = iconRes;
            this.channelId = channelId;
        }
    }

    private NotificationStyle styleFor(String type) {
        switch (type) {
            case "JOB_ASSIGNED":
                return new NotificationStyle(R.drawable.ic_notification_assigned, CHANNEL_JOBS);
            case "JOB_RECEIVED":
                return new NotificationStyle(R.drawable.ic_receipt, CHANNEL_JOBS);
            case "ADMIN_BROADCAST":
                return new NotificationStyle(R.drawable.ic_notification_announcement, CHANNEL_ANNOUNCEMENTS);
            default:
                return new NotificationStyle(R.drawable.ic_notification, CHANNEL_ANNOUNCEMENTS);
        }
    }

    private void createChannelsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (manager.getNotificationChannel(CHANNEL_JOBS) == null) {
            NotificationChannel jobs = new NotificationChannel(
                    CHANNEL_JOBS, "Job Updates", NotificationManager.IMPORTANCE_HIGH);
            jobs.setDescription("New jobs received and jobs assigned to you");
            jobs.enableVibration(true);
            jobs.setVibrationPattern(VIBRATION_PATTERN);
            manager.createNotificationChannel(jobs);
        }
        if (manager.getNotificationChannel(CHANNEL_ANNOUNCEMENTS) == null) {
            NotificationChannel announcements = new NotificationChannel(
                    CHANNEL_ANNOUNCEMENTS, "Announcements", NotificationManager.IMPORTANCE_HIGH);
            announcements.setDescription("Messages sent by the app administrator");
            announcements.enableVibration(true);
            announcements.setVibrationPattern(VIBRATION_PATTERN);
            manager.createNotificationChannel(announcements);
        }
    }

    /** Sends the current device's FCM token to the backend. Safe to call
     * repeatedly (e.g. after every login) - the backend simply overwrites
     * the stored token for this user. */
    public static void syncTokenWithServer(Context context) {
        SessionManager session = new SessionManager(context);
        if (!session.isLoggedIn()) return;
        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        Log.w(TAG, "Fetching FCM token failed", task.getException());
                        return;
                    }
                    String token = task.getResult();
                    JsonObject body = new JsonObject();
                    body.addProperty("fcm_token", token);
                    ApiClient.get(context).registerFcmToken(body).enqueue(new Callback<JsonObject>() {
                        @Override
                        public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                            if (!res.isSuccessful()) Log.w(TAG, "FCM token registration failed: " + res.code());
                        }
                        @Override
                        public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                            Log.w(TAG, "FCM token registration network error", t);
                        }
                    });
                });
    }
}