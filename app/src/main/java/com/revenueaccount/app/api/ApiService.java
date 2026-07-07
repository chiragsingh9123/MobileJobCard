package com.revenueaccount.app.api;

import com.google.gson.JsonObject;
import java.util.Map;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    // ---------- AUTH ----------
    @POST("api/auth/send-otp/")
    Call<JsonObject> sendOtp(@Body Map<String, String> body);

    @POST("api/auth/register/")
    Call<JsonObject> register(@Body Map<String, String> body);

    @POST("api/auth/login/")
    Call<JsonObject> login(@Body Map<String, String> body);

    @GET("api/auth/me/")
    Call<JsonObject> me();

    @GET("api/auth/plans/")
    Call<JsonObject> plans();

    @POST("api/auth/redeem-voucher/")
    Call<JsonObject> redeemVoucher(@Body Map<String, String> body);

    // ---------- CUSTOMERS ----------
    @GET("api/customers/lookup/")
    Call<JsonObject> lookupCustomer(@Query("mobile") String mobile);

    @GET("api/customers/")
    Call<JsonObject> customers(@Query("search") String search,
    @Query("pending") String pending);

    @POST("api/customers/")
    Call<JsonObject> createCustomer(@Body Map<String, String> body);

    @GET("api/customers/{id}/history/")
    Call<JsonObject> customerHistory(@Path("id") long id);

    // ---------- JOBS ----------
    @POST("api/jobs/")
    Call<JsonObject> createJob(@Body JsonObject body);

    @GET("api/jobs/")
    Call<JsonObject> jobs(@Query("status") String status, @Query("search") String search);

    @GET("api/jobs/")
    Call<JsonObject> jobsFiltered(@Query("status") String status, @Query("search") String search,
    @Query("date") String date, @Query("date_from") String dateFrom,
    @Query("date_to") String dateTo);

    @GET("api/jobs/")
    Call<JsonObject> jobsByStaff(@Query("assigned_to") String assignedTo);

    @GET("api/jobs/{id}/")
    Call<JsonObject> jobDetails(@Path("id") long id);

    @POST("api/jobs/{id}/update_status/")
    Call<JsonObject> updateJobStatus(@Path("id") long id, @Body JsonObject body);

    @POST("api/jobs/{id}/update/")
    Call<JsonObject> updateJob(@Path("id") long id, @Body JsonObject body);

    @POST("api/jobs/{id}/assign/")
    Call<JsonObject> assignJob(@Path("id") long id, @Body Map<String, Long> body);

    @GET("api/jobs/{id}/activity/")
    Call<JsonObject> jobActivity(@Path("id") long id);

    @Multipart
    @POST("api/jobs/{id}/media/")
    Call<JsonObject> uploadJobMedia(@Path("id") long id, @Part("caption") RequestBody caption,
    @Part MultipartBody.Part file);

    @Multipart
    @POST("api/jobs/{id}/media/")
    Call<JsonObject> uploadJobMediaWithCategory(@Path("id") long id,
    @Part("caption") RequestBody caption, @Part("category") RequestBody category,
    @Part MultipartBody.Part file);

    @GET("api/jobs/{id}/media/")
    Call<JsonObject> listJobMedia(@Path("id") long id);

    @GET("api/jobs/{id}/media/")
    Call<JsonObject> listJobMediaByCategory(@Path("id") long id, @Query("category") String category);

    @POST("api/jobs/{id}/media/{mediaId}/delete/")
    Call<JsonObject> deleteJobMedia(@Path("id") long id, @Path("mediaId") long mediaId);

    @GET("api/jobs/{id}/message/{type}/")
    Call<JsonObject> getJobMessage(@Path("id") long id, @Path("type") String type);

    // ---------- APP VERSION CHECK (force update) ----------
    @GET("api/app/version-check/")
    Call<JsonObject> versionCheck(@Query("version_code") int versionCode);

    // ---------- PAYMENTS & KHATA ----------
    @POST("api/payments/payments/")
    Call<JsonObject> createPayment(@Body JsonObject body);

    @GET("api/payments/khata/pending/")
    Call<JsonObject> khataPending();

    @GET("api/payments/khata/jobs/{customerId}/")
    Call<JsonObject> khataJobsForCustomer(@Path("customerId") long customerId);

    @POST("api/payments/expenses/")
    Call<JsonObject> createExpense(@Body JsonObject body);

    @GET("api/payments/expenses/")
    Call<JsonObject> listExpenses();

    // ---------- INVENTORY ----------
    @GET("api/inventory/products/")
    Call<JsonObject> products(@Query("category") String category,
    @Query("stock") String stock,
    @Query("search") String search);

    @POST("api/inventory/products/")
    Call<JsonObject> createProduct(@Body JsonObject body);

    @GET("api/inventory/products/summary/")
    Call<JsonObject> productsSummary();

    @POST("api/inventory/products/{id}/add_stock/")
    Call<JsonObject> addStock(@Path("id") long id, @Body Map<String, Integer> body);

    // ---------- REPORTS ----------
    @GET("api/reports/dashboard/")
    Call<JsonObject> dashboard();

    @GET("api/reports/analytics/")
    Call<JsonObject> analytics(@Query("days") int days);

    // ---------- STAFF MANAGEMENT ----------
    @GET("api/staff/")
    Call<JsonObject> listStaff();

    @POST("api/staff/")
    Call<JsonObject> createStaff(@Body Map<String, String> body);

    @POST("api/staff/{id}/toggle/")
    Call<JsonObject> toggleStaff(@Path("id") long id);

    @POST("api/staff/{id}/reset-password/")
    Call<JsonObject> resetStaffPassword(@Path("id") long id, @Body Map<String, String> body);

    @GET("api/staff/{id}/jobs/")
    Call<JsonObject> staffJobs(@Path("id") long id);

    // ---------- UPI SUBSCRIPTION PAYMENT ----------
    @GET("api/subscription/upi-details/")
    Call<JsonObject> upiDetails();

    @Multipart
    @POST("api/subscription/submit-payment/")
    Call<JsonObject> submitPayment(@Part("plan_id") RequestBody planId,
    @Part("utr_number") RequestBody utrNumber,
    @Part MultipartBody.Part screenshot);

    @GET("api/subscription/my-payment-requests/")
    Call<JsonObject> myPaymentRequests();

    // ---------- SHOP SETTINGS ----------
    @GET("api/shop/profile/")
    Call<JsonObject> shopProfile();

    @POST("api/shop/profile/")
    Call<JsonObject> updateShopProfile(@Body Map<String, String> body);

    @POST("api/shop/print-settings/")
    Call<JsonObject> updatePrintSettings(@Body JsonObject body);

    @POST("api/shop/whatsapp-templates/")
    Call<JsonObject> updateTemplates(@Body Map<String, String> body);

    @POST("api/shop/change-password/")
    Call<JsonObject> changePassword(@Body Map<String, String> body);

    @GET("api/shop/activity-log/")
    Call<JsonObject> shopActivityLog();

    @GET("api/shop/export/")
    Call<JsonObject> exportBackup();
}
