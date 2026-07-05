package com.revenueaccount.app.activities;

import com.revenueaccount.app.utils.AppToast;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.graphics.Color;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.revenueaccount.app.R;
import com.revenueaccount.app.adapters.JobAdapter;
import com.revenueaccount.app.api.ApiClient;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Screen 6: Job Details — status timeline, customer call, staff assignment,
 * edit job, photo/video attachments, send message (WhatsApp/SMS, multiple templates),
 * full activity log, status update (with RWR reason+payment), deliver */
public class JobDetailsActivity extends AppCompatActivity {

    private static final String TAG = "JobDetailsActivity";
    private static final String[] TIMELINE = {"RECEIVED", "REPAIRING", "READY", "DELIVERED"};
    private static final String[] ALL_STATUSES =
    {"RECEIVED", "REPAIRING", "WAITING_PARTS", "READY", "DELIVERED", "RWR"};

    private static final int REQ_EDIT_JOB = 701;
    private static final int REQ_CAPTURE_PHOTO = 702;
    private static final int REQ_CAPTURE_VIDEO = 703;
    private static final int REQ_PICK_MEDIA = 704;

    private JsonObject job;
    private long jobPk;
    private Uri pendingCaptureUri;
    private String pendingCaptureType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_job_details);
        jobPk = getIntent().getLongExtra("job_pk", -1);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnEdit).setOnClickListener(v -> {
            Intent i = new Intent(this, EditJobActivity.class);
            i.putExtra("job_pk", jobPk);
            startActivityForResult(i, REQ_EDIT_JOB);
        });

        findViewById(R.id.btnCall).setOnClickListener(v -> {
            if (job == null) return;
            String mobile = job.getAsJsonObject("customer").get("mobile").getAsString();
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + mobile)));
        });

        findViewById(R.id.btnUpdateStatus).setOnClickListener(v -> showStatusDialog());
        findViewById(R.id.btnAssign).setOnClickListener(v -> showAssignDialog());
        findViewById(R.id.btnSendMessage).setOnClickListener(v -> showMessageTypeDialog());
        findViewById(R.id.btnAddPhoto).setOnClickListener(v -> showAddMediaDialog("PHOTO"));
        findViewById(R.id.btnAddVideo).setOnClickListener(v -> showAddMediaDialog("VIDEO"));

        findViewById(R.id.btnDeliver).setOnClickListener(v -> {
            if (job == null) return;
            Intent i = new Intent(this, DeliveryActivity.class);
            i.putExtra("job_pk", jobPk);
            i.putExtra("job_id", job.get("job_id").getAsString());
            i.putExtra("customer_id", job.getAsJsonObject("customer").get("id").getAsLong());
            i.putExtra("total_cost", job.get("estimated_cost").getAsDouble());
            i.putExtra("paid_amount", job.get("paid_amount").getAsDouble());
            startActivity(i);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        ApiClient.get(this).jobDetails(jobPk).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                if (res.isSuccessful() && res.body() != null) {
                    job = res.body();
                    try {
                        render();
                    } catch (Exception e) {
                        Log.e(TAG, "Render error", e);
                        AppToast.error(JobDetailsActivity.this, "Data dikhane me dikkat");
                    }
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                AppToast.error(JobDetailsActivity.this, "Network error");
            }
        });
    }

    private void render() {
        JsonObject c = job.getAsJsonObject("customer");
        String status = job.get("status").getAsString();

        String jobTitle = job.get("job_id").getAsString();
        if (job.has("custom_name") && !job.get("custom_name").getAsString().isEmpty()) {
            jobTitle += " — " + job.get("custom_name").getAsString();
        }
        set(R.id.tvJobId, jobTitle);
        TextView tvStatus = findViewById(R.id.tvStatus);
        tvStatus.setText(status.replace("_", " "));
        tvStatus.setTextColor(JobAdapter.statusColor(status));
        set(R.id.tvDate, "Banaya: " + job.get("created_at").getAsString()
        .substring(0, 16).replace("T", " "));

        String name = c.get("name").getAsString();
        set(R.id.tvCustName, name);
        String mobileLine = c.get("mobile").getAsString()
        + (c.get("address").getAsString().isEmpty() ? "" : " • " + c.get("address").getAsString());
        set(R.id.tvCustMobile, mobileLine);
        set(R.id.tvCustAvatar, name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());

        StringBuilder dev = new StringBuilder(" " + job.get("device_brand").getAsString()
        + " " + job.get("device_model").getAsString());
        if (!job.get("color").getAsString().isEmpty()) dev.append(" • ").append(job.get("color").getAsString());
        if (!job.get("storage").getAsString().isEmpty()) dev.append(" • ").append(job.get("storage").getAsString());
        if (!job.get("imei1").getAsString().isEmpty())
        dev.append("\nIMEI 1: ").append(job.get("imei1").getAsString());
        if (!job.get("imei2").getAsString().isEmpty())
        dev.append("\nIMEI 2: ").append(job.get("imei2").getAsString());
        if (!job.get("accessories").getAsString().isEmpty())
        dev.append("\n Accessories: ").append(job.get("accessories").getAsString());
        String lockType = job.has("lock_type") ? job.get("lock_type").getAsString() : "NONE";
        if (!"NONE".equals(lockType) && !job.get("device_password").getAsString().isEmpty()) {
            dev.append("\n Lock: ").append(lockType.equals("PIN") ? "PIN — " : "Pattern — ")
            .append(job.get("device_password").getAsString());
        }
        if (job.has("alternate_mobile") && !job.get("alternate_mobile").getAsString().isEmpty()) {
            dev.append("\n Alternate: ").append(job.get("alternate_mobile").getAsString());
        }
        dev.append("\n\n Problem: ").append(job.get("problem").getAsString());
        if (job.has("diagnosis") && !job.get("diagnosis").getAsString().isEmpty()) {
            dev.append("\n Diagnosis: ").append(job.get("diagnosis").getAsString());
        }
        if ("RWR".equals(status) && job.has("rwr_reason") && !job.get("rwr_reason").getAsString().isEmpty()) {
            dev.append("\n\n RWR Reason: ").append(job.get("rwr_reason").getAsString());
            boolean payReq = job.has("rwr_payment_required") && job.get("rwr_payment_required").getAsBoolean();
            dev.append("\n Payment Required: ").append(payReq ? "Haan" : "Nahi");
        }
        set(R.id.tvDeviceInfo, dev.toString());

        double est = job.get("estimated_cost").getAsDouble();
        double paid = job.get("paid_amount").getAsDouble();
        double bal = job.get("balance_amount").getAsDouble();
        set(R.id.tvPayInfo, " Estimated Cost: ₹" + est
        + "\n Paid: ₹" + paid
        + "\n" + (bal > 0 ? " Balance: ₹" + bal : " Poora payment ho gaya"));

        TextView tvAssigned = findViewById(R.id.tvAssignedTo);
        if (job.has("assigned_to") && !job.get("assigned_to").isJsonNull()) {
            JsonObject staff = job.getAsJsonObject("assigned_to");
            String sName = (staff.has("first_name") ? staff.get("first_name").getAsString() : "");
            tvAssigned.setText(sName + (staff.has("designation") && !staff.get("designation").getAsString().isEmpty()
            ? " (" + staff.get("designation").getAsString() + ")" : ""));
        } else {
            tvAssigned.setText("Assign nahi hua");
        }

        buildTimeline(status);
        renderActivityLog();
        loadMedia();

        View btnDeliver = findViewById(R.id.btnDeliver);
        btnDeliver.setVisibility("DELIVERED".equals(status) ? View.GONE : View.VISIBLE);
    }

    private void buildTimeline(String status) {
        LinearLayout timeline = findViewById(R.id.timeline);
        timeline.removeAllViews();
        int reached = 0;
        for (int i = 0; i < TIMELINE.length; i++) if (TIMELINE[i].equals(status)) reached = i;
        if ("WAITING_PARTS".equals(status)) reached = 1;
        if ("RWR".equals(status)) reached = 3;
        for (int i = 0; i < TIMELINE.length; i++) {
            TextView dot = new TextView(this);
            dot.setText((i <= reached ? "●" : "○") + "\n" + TIMELINE[i].substring(0, 1)
            + TIMELINE[i].substring(1).toLowerCase());
            dot.setTextSize(11f);
            dot.setGravity(Gravity.CENTER);
            dot.setTextColor(i <= reached ? Color.parseColor("#4CAF50") : Color.parseColor("#9E9E9E"));
            LinearLayout.LayoutParams lp =
            new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            dot.setLayoutParams(lp);
            timeline.addView(dot);
        }
    }

    /** Poori activity/audit trail load + dikhao */
    private void renderActivityLog() {
        ApiClient.get(this).jobActivity(jobPk).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                LinearLayout container = findViewById(R.id.activityContainer);
                container.removeAllViews();
                if (!res.isSuccessful() || res.body() == null) return;
                try {
                    JsonArray activity = res.body().getAsJsonArray("activity");
                    for (int i = 0; i < activity.size(); i++) {
                        JsonObject a = activity.get(i).getAsJsonObject();
                        addActivityRow(container, a);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Activity render error", e);
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {}
        });
    }

    private void addActivityRow(LinearLayout container, JsonObject a) {
        String action = a.has("action") ? a.get("action").getAsString() : "";
        String icon;
        switch (action) {
            case "CREATED": icon = "🆕"; break;
            case "STATUS": icon = ""; break;
            case "ASSIGNED": icon = ""; break;
            case "PAYMENT": icon = ""; break;
            case "NOTE": icon = ""; break;
            case "EDITED": icon = ""; break;
            default: icon = "•";
        }
        TextView tv = new TextView(this);
        String by = a.has("by_user") ? a.get("by_user").getAsString() : "System";
        String desc = a.has("description") ? a.get("description").getAsString() : "";
        String date = a.has("created_at") ? a.get("created_at").getAsString() : "";
        String dateShort = date.length() >= 16 ? date.substring(0, 16).replace("T", " ") : date;
        tv.setText(icon + " " + desc + "\n" + dateShort + " • " + by);
        tv.setTextSize(13f);
        tv.setLineSpacing(4f, 1f);
        tv.setTextColor(Color.parseColor("#212121"));
        tv.setBackgroundColor(Color.WHITE);
        tv.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 6);
        tv.setLayoutParams(lp);
        container.addView(tv);
    }

    // ================= STAFF ASSIGN =================

    private void showAssignDialog() {
        ApiClient.get(this).listStaff().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                if (!res.isSuccessful() || res.body() == null) return;
                try {
                    JsonArray staffList = res.body().getAsJsonArray("results");
                    String[] labels = new String[staffList.size() + 1];
                    long[] ids = new long[staffList.size() + 1];
                    labels[0] = " Unassign";
                    ids[0] = -1;
                    for (int i = 0; i < staffList.size(); i++) {
                        JsonObject s = staffList.get(i).getAsJsonObject();
                        labels[i + 1] = s.get("first_name").getAsString()
                        + " (" + s.get("role").getAsString() + ")";
                        ids[i + 1] = s.get("id").getAsLong();
                    }
                    new AlertDialog.Builder(JobDetailsActivity.this)
                    .setTitle("Kisko assign karein?")
                    .setItems(labels, (d, which) -> assignTo(ids[which]))
                    .show();
                } catch (Exception e) {
                    Log.e(TAG, "Staff list error", e);
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                AppToast.error(JobDetailsActivity.this, "Network error");
            }
        });
    }

    private void assignTo(long staffId) {
        Map<String, Long> body = new HashMap<>();
        if (staffId > 0) body.put("staff_id", staffId);
        ApiClient.get(this).assignJob(jobPk, body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                if (res.isSuccessful() && res.body() != null) {
                    job = res.body();
                    try { render(); } catch (Exception e) { Log.e(TAG, "Render error", e); }
                    AppToast.success(JobDetailsActivity.this, " Assign ho gaya");
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                AppToast.error(JobDetailsActivity.this, "Network error");
            }
        });
    }

    // ================= SEND MESSAGE (multiple templates + WhatsApp/SMS) =================

    private void showMessageTypeDialog() {
        if (job == null) return;
        String[] types = {"received", "ready", "delivered", "rwr"};
        String[] labels = {" Job Received", " Device Ready", " Delivered", " RWR (Return Without Repair)"};
        new AlertDialog.Builder(this)
        .setTitle("Kaunsa message bhejna hai?")
        .setItems(labels, (d, which) -> fetchAndSendMessage(types[which]))
        .show();
    }

    private void fetchAndSendMessage(String type) {
        ApiClient.get(this).getJobMessage(jobPk, type).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                if (res.isSuccessful() && res.body() != null) {
                    try {
                        String text = res.body().get("message").getAsString();
                        String mobile = res.body().get("customer_mobile").getAsString();
                        showChannelDialog(text, mobile);
                    } catch (Exception e) {
                        Log.e(TAG, "Message fetch parse error", e);
                        AppToast.error(JobDetailsActivity.this, "Message ready nahi ho paya");
                    }
                } else {
                    AppToast.error(JobDetailsActivity.this, "Message ready nahi ho paya");
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                AppToast.error(JobDetailsActivity.this, "Network error");
            }
        });
    }

    private void showChannelDialog(String text, String mobile) {
        new AlertDialog.Builder(this)
        .setTitle("Kaise bhejein?")
        .setMessage(text)
        .setPositiveButton(" WhatsApp", (d, w) -> sendViaWhatsapp(text, mobile))
        .setNegativeButton(" SMS", (d, w) -> sendViaSms(text, mobile))
        .setNeutralButton("Cancel", null)
        .show();
    }

    private void sendViaWhatsapp(String message, String mobile) {
        try {
            String clean = mobile.replaceAll("[^0-9]", "");
            String url = "https://wa.me/91" + clean + "?text=" + Uri.encode(message);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Log.e(TAG, "WhatsApp send error", e);
            AppToast.error(this, "WhatsApp open nahi ho paya");
        }
    }

    private void sendViaSms(String message, String mobile) {
        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + mobile));
            intent.putExtra("sms_body", message);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "SMS send error", e);
            AppToast.error(this, "SMS app nahi khula");
        }
    }

    // ================= PHOTOS & VIDEOS =================

    private void loadMedia() {
        ApiClient.get(this).listJobMedia(jobPk).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                LinearLayout container = findViewById(R.id.mediaContainer);
                container.removeAllViews();
                TextView tvNoMedia = findViewById(R.id.tvNoMedia);
                if (!res.isSuccessful() || res.body() == null) return;
                try {
                    JsonArray results = res.body().getAsJsonArray("results");
                    tvNoMedia.setVisibility(results.size() == 0 ? View.VISIBLE : View.GONE);
                    for (int i = 0; i < results.size(); i++) {
                        addMediaThumb(container, results.get(i).getAsJsonObject());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Media render error", e);
                }
            }
            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {}
        });
    }

    /** FIX: pehle sirf generic icon dikhta tha, actual photo/video content nahi.
     * Ab Glide se real image load hoti hai, aur tap karne par photo full-screen open hoti hai
     * ya video device ke player me play hoti hai. */
    private void addMediaThumb(LinearLayout container, JsonObject media) {
        View thumb = LayoutInflater.from(this).inflate(R.layout.item_media_thumb, container, false);
        android.widget.ImageView iv = thumb.findViewById(R.id.ivThumb);
        TextView videoBadge = thumb.findViewById(R.id.tvVideoBadge);
        boolean isVideo = media.has("media_type") && "VIDEO".equals(media.get("media_type").getAsString());
        videoBadge.setVisibility(isVideo ? View.VISIBLE : View.GONE);

        String relUrl = (media.has("url") && !media.get("url").isJsonNull()) ? media.get("url").getAsString() : null;
        String fullUrl = relUrl != null ? ApiClient.BASE_URL.replaceAll("/$", "") + relUrl : null;

        if (isVideo) {
            // Video ka asli frame remote URL se reliably nikalna mushkil hai, isliye play-icon
            // dikhate hain — par tap karne par video WAKAI play hoti hai (device ke player me).
            iv.setImageResource(R.drawable.ic_video);
        } else if (fullUrl != null) {
            com.bumptech.glide.Glide.with(this)
            .load(fullUrl)
            .placeholder(R.drawable.ic_photo)
            .error(R.drawable.ic_photo)
            .centerCrop()
            .into(iv);
        } else {
            iv.setImageResource(R.drawable.ic_photo);
        }

        long mediaId = media.has("id") ? media.get("id").getAsLong() : -1;
        thumb.findViewById(R.id.btnDeleteMedia).setOnClickListener(v -> confirmDeleteMedia(mediaId));
        if (fullUrl != null) {
            String finalUrl = fullUrl;
            thumb.setOnClickListener(v -> viewMedia(finalUrl, isVideo));
        }
        container.addView(thumb);
    }

    /** Photo full-screen dialog me dikhti hai; video device ke default player me khulti hai */
    private void viewMedia(String url, boolean isVideo) {
        try {
            if (isVideo) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse(url), "video/*");
                 intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                 startActivity(intent);
                 } else {
                 android.widget.ImageView fullImage = new android.widget.ImageView(this);
                 fullImage.setAdjustViewBounds(true);
                 fullImage.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                 com.bumptech.glide.Glide.with(this).load(url).into(fullImage);
                 new AlertDialog.Builder(this)
                 .setView(fullImage)
                 .setPositiveButton("Band Karein", null)
                 .show();
                 }
                 } catch (Exception e) {
                 Log.e(TAG, "View media error", e);
                 AppToast.error(this, "Kholne me dikkat aayi");
                 }
                 }

                 private void confirmDeleteMedia(long mediaId) {
                 if (mediaId < 0) return;
                 new AlertDialog.Builder(this)
                 .setTitle("Photo/Video delete karein?")
                 .setPositiveButton("Delete", (d, w) -> deleteMedia(mediaId))
                 .setNegativeButton("Cancel", null)
                 .show();
                 }

                 private void deleteMedia(long mediaId) {
                 ApiClient.get(this).deleteJobMedia(jobPk, mediaId).enqueue(new Callback<JsonObject>() {
                 @Override
                 public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                 if (res.isSuccessful()) {
                 AppToast.success(JobDetailsActivity.this, "Delete ho gaya");
                 loadMedia();
                 }
                 }
                 @Override
                 public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                 AppToast.error(JobDetailsActivity.this, "Network error");
                 }
                 });
                 }

                 private void showAddMediaDialog(String type) {
                 String[] options = {" Camera se " + ("PHOTO".equals(type) ? "khichein" : "record karein"),
                 " Gallery se chunein"};
                 pendingCaptureType = type;
                 new AlertDialog.Builder(this)
                 .setTitle("PHOTO".equals(type) ? "Photo Add Karein" : "Video Add Karein")
                 .setItems(options, (d, which) -> {
                 if (which == 0) capture(type); else pickFromGallery(type);
                 })
                 .show();
                 }

                 private void capture(String type) {
                 try {
                 File dir = new File(getCacheDir(), "media_capture");
                 if (!dir.exists()) dir.mkdirs();
                 String ext = "PHOTO".equals(type) ? ".jpg" : ".mp4";
                 File file = File.createTempFile("capture_", ext, dir);
                 pendingCaptureUri = FileProvider.getUriForFile(this,
                 getPackageName() + ".fileprovider", file);
                 Intent intent = new Intent("PHOTO".equals(type)
                 ? MediaStore.ACTION_IMAGE_CAPTURE : MediaStore.ACTION_VIDEO_CAPTURE);
                 intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCaptureUri);
                 intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                 startActivityForResult(intent, "PHOTO".equals(type) ? REQ_CAPTURE_PHOTO : REQ_CAPTURE_VIDEO);
                 } catch (IOException e) {
                 Log.e(TAG, "Capture setup error", e);
                 AppToast.error(this, "Camera open nahi ho paya");
                 }
                 }

                 private void pickFromGallery(String type) {
                 Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                 intent.setType("PHOTO".equals(type) ? "image/*" : "video/*");
                 startActivityForResult(intent, REQ_PICK_MEDIA);
                 }

                 @Override
                 protected void onActivityResult(int requestCode, int resultCode, Intent data) {
                 super.onActivityResult(requestCode, resultCode, data);
                 if (resultCode != RESULT_OK) return;

                 if (requestCode == REQ_EDIT_JOB) {
                 load();
                 } else if (requestCode == REQ_CAPTURE_PHOTO || requestCode == REQ_CAPTURE_VIDEO) {
                 if (pendingCaptureUri != null) uploadMedia(pendingCaptureUri, pendingCaptureType);
                 } else if (requestCode == REQ_PICK_MEDIA) {
                 if (data != null && data.getData() != null) uploadMedia(data.getData(), pendingCaptureType);
                 }
                 }

                 private void uploadMedia(Uri uri, String type) {
                 try {
                 String mimeType = getContentResolver().getType(uri);
                 if (mimeType == null) mimeType = "PHOTO".equals(type) ? "image/jpeg" : "video/mp4";
                 java.io.InputStream in = getContentResolver().openInputStream(uri);
                 if (in == null) { AppToast.error(this, "File nahi mil paayi"); return; }
                 byte[] bytes = readAllBytes(in);
                 in.close();
                 RequestBody fileBody = RequestBody.create(MediaType.parse(mimeType), bytes);
                 String ext = "PHOTO".equals(type) ? "jpg" : "mp4";
                 MultipartBody.Part part = MultipartBody.Part.createFormData("file",
                 "upload_" + System.currentTimeMillis() + "." + ext, fileBody);
                 RequestBody caption = RequestBody.create(MediaType.parse("text/plain"), "");
                 ApiClient.get(this).uploadJobMedia(jobPk, caption, part).enqueue(new Callback<JsonObject>() {
                 @Override
                 public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                 if (res.isSuccessful()) {
                 AppToast.success(JobDetailsActivity.this, " Add ho gaya");
                 loadMedia();
                 } else {
                 AppToast.error(JobDetailsActivity.this, "Upload nahi hua");
                 }
                 }
                 @Override
                 public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                 AppToast.error(JobDetailsActivity.this, "Network error — upload fail hua");
                 }
                 });
                 } catch (Exception e) {
                 Log.e(TAG, "Upload media error", e);
                 AppToast.error(this, "Upload me dikkat aayi");
                 }
                 }

                 private byte[] readAllBytes(java.io.InputStream in) throws IOException {
                 java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                 byte[] data = new byte[8192];
                 int read;
                 while ((read = in.read(data, 0, data.length)) != -1) buffer.write(data, 0, read);
                 return buffer.toByteArray();
                 }

                 // ================= STATUS UPDATE ( RWR reason + payment capture) =================

                 private void showStatusDialog() {
                 String[] selectable = {"RECEIVED", "REPAIRING", "WAITING_PARTS", "READY", "RWR"};
                 String[] labels = new String[selectable.length];
                 for (int i = 0; i < selectable.length; i++) labels[i] = selectable[i].replace("_", " ");
                 new AlertDialog.Builder(this)
                 .setTitle("Naya status chunein")
                 .setItems(labels, (d, which) -> {
                 if ("RWR".equals(selectable[which])) showRwrDialog();
                 else updateStatus(selectable[which], null, false, 0);
                 })
                 .setNeutralButton("Deliver karna hai?", (d, w) -> {
                 if (job == null) return;
                 Intent i = new Intent(this, DeliveryActivity.class);
                 i.putExtra("job_pk", jobPk);
                 i.putExtra("job_id", job.get("job_id").getAsString());
                 i.putExtra("customer_id", job.getAsJsonObject("customer").get("id").getAsLong());
                 i.putExtra("total_cost", job.get("estimated_cost").getAsDouble());
                 i.putExtra("paid_amount", job.get("paid_amount").getAsDouble());
                 startActivity(i);
                 })
                 .show();
                 }

                /** RWR: reason + customer ko payment karna hai ya nahi, capture karta hai */
                private void showRwrDialog() {
                    View view = LayoutInflater.from(this).inflate(R.layout.dialog_rwr, null);
                    EditText etReason = view.findViewById(R.id.etRwrReason);
                    RadioGroup rgPayment = view.findViewById(R.id.rgPaymentRequired);
                    View amountLayout = view.findViewById(R.id.tilRwrAmount);
                    EditText etAmount = view.findViewById(R.id.etRwrAmount);

                    rgPayment.setOnCheckedChangeListener((group, checkedId) -> {
                        boolean yes = checkedId == R.id.rbPaymentYes;
                        amountLayout.setVisibility(yes ? View.VISIBLE : View.GONE);
                    });

                    new AlertDialog.Builder(this)
                    .setTitle(" RWR — Return Without Repair")
                    .setView(view)
                    .setPositiveButton("Confirm", (d, w) -> {
                        String reason = etReason.getText().toString().trim();
                        if (reason.isEmpty()) {
                            AppToast.warning(this, "RWR ka reason daalna zaroori hai");
                            return;
                        }
                        boolean paymentRequired = rgPayment.getCheckedRadioButtonId() == R.id.rbPaymentYes;
                        double amount = paymentRequired
                        ? com.revenueaccount.app.utils.NumUtils.parseDouble(etAmount.getText().toString())
                        : 0;
                        updateStatus("RWR", reason, paymentRequired, amount);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                }

                private void updateStatus(String status, String rwrReason, boolean rwrPaymentRequired, double rwrAmount) {
                    JsonObject body = new JsonObject();
                    body.addProperty("status", status);
                    if ("RWR".equals(status)) {
                        body.addProperty("rwr_reason", rwrReason);
                        body.addProperty("rwr_payment_required", rwrPaymentRequired);
                        body.addProperty("rwr_amount", rwrAmount);
                    }
                    ApiClient.get(this).updateJobStatus(jobPk, body).enqueue(new Callback<JsonObject>() {
                        @Override
                        public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> res) {
                            if (res.isSuccessful() && res.body() != null) {
                                job = res.body();
                                try {
                                    render();
                                    String msg = "Status update hua: " + status;
                                    if (job.has("message") && !job.get("message").isJsonNull()) {
                                        msg += "\n" + job.get("message").getAsString();
                                    }
                                    AppToast.success(JobDetailsActivity.this, msg);
                                } catch (Exception e) {
                                    Log.e(TAG, "Status render error", e);
                                }
                            } else {
                                AppToast.error(JobDetailsActivity.this, "Status update nahi hua");
                            }
                        }
                        @Override
                        public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                            AppToast.error(JobDetailsActivity.this, "Network error");
                        }
                    });
                }

                private void set(int id, String v) { ((TextView) findViewById(id)).setText(v); }
            }
