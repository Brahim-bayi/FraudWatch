# ==============================
# FraudWatch ProGuard Rules
# ==============================

# --- Modèles de données ---
-keep class com.fraudwatch.data.model.** { *; }
-keepclassmembers class com.fraudwatch.data.model.** { *; }

# --- Firebase ---
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# --- Firestore ---
-keep class * extends com.google.firebase.firestore.** { *; }
-keepnames class com.google.firebase.firestore.** { *; }

# --- Retrofit + OkHttp ---
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations

# --- Gson ---
-keep class com.google.gson.** { *; }
-keepattributes EnclosingMethod

# --- Glide ---
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { *; }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# --- Navigation Component ---
-keep class androidx.navigation.** { *; }

# --- CameraX ---
-keep class androidx.camera.** { *; }

# --- Général ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
