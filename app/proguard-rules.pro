# ===== Retrofit / OkHttp / Gson =====
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn okio.**
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes Exceptions
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# ===== Razorpay Checkout =====
-keep class com.razorpay.** { *; }
-keepclassmembers class * {
  @android.webkit.JavascriptInterface <methods>;
}
-dontwarn com.razorpay.**
-optimizations !method/removal/parameter

-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Our own app package - safe to keep in full (small app, negligible size impact,
# avoids any risk of reflection-based fields getting stripped incorrectly)
-keep class com.revenueaccount.app.** { *; }

# ===== Glide (image loading) =====
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.resource.bitmap.ImageHeaderParser$ImageType {
    **[] $VALUES;
    public *;
}
-dontwarn com.bumptech.glide.**

# ===== MPAndroidChart =====
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# ===== ZXing barcode scanner =====
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.google.zxing.**
-dontwarn com.journeyapps.barcodescanner.**

# ===== General Android hygiene =====
-keepattributes SourceFile,LineNumberTable
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}
