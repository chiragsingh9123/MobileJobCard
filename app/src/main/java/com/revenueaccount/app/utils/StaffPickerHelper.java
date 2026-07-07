package com.revenueaccount.app.utils;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.revenueaccount.app.api.ApiClient;
import java.util.WeakHashMap;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Reusable helper - shop ke saare staff members ko ek Spinner me load karta hai.
 * "Job Card kaun save kar raha hai", "Status kaun badal raha hai", "Kaun deliver kar
 * raha hai" jaise saare popups isi helper se banate hain.
 */
public class StaffPickerHelper {

    /** Naam jaanboojh kar 'Callback' nahi rakha - retrofit2.Callback se clash hota tha */
    public interface StaffLoadCallback {
        void onLoaded();
        void onError();
    }

    private static final Map<Spinner, long[]> idsMap = new WeakHashMap<>();

    public static void populate(Context ctx, Spinner spinner, StaffLoadCallback callback) {
        ApiClient.get(ctx).listStaff().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> res) {
                if (!res.isSuccessful() || res.body() == null) {
                    if (callback != null) callback.onError();
                    return;
                }
                try {
                    JsonArray results = res.body().getAsJsonArray("results");
                    long[] ids = new long[results.size()];
                    String[] names = new String[results.size()];
                    for (int i = 0; i < results.size(); i++) {
                        JsonObject s = results.get(i).getAsJsonObject();
                        ids[i] = s.has("id") ? s.get("id").getAsLong() : -1;
                        String name = s.has("first_name") ? s.get("first_name").getAsString() : "?";
                        String designation = s.has("designation") && !s.get("designation").isJsonNull()
                                ? s.get("designation").getAsString() : "";
                        names[i] = designation.isEmpty() ? name : name + " (" + designation + ")";
                    }
                    idsMap.put(spinner, ids);
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(ctx,
                            android.R.layout.simple_spinner_dropdown_item, names);
                    spinner.setAdapter(adapter);
                    if (callback != null) callback.onLoaded();
                } catch (Exception e) {
                    if (callback != null) callback.onError();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                if (callback != null) callback.onError();
            }
        });
    }

    /** Spinner me abhi jo staff select hai, uski ID nikalta hai (-1 agar kuch load nahi hua) */
    public static long getSelectedStaffId(Spinner spinner) {
        long[] ids = idsMap.get(spinner);
        if (ids == null || ids.length == 0) return -1;
        int pos = spinner.getSelectedItemPosition();
        if (pos < 0 || pos >= ids.length) return -1;
        return ids[pos];
    }
}
