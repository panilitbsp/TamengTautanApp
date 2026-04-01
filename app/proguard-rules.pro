# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Menjaga nama class dan metode asli untuk model data TamengTautan agar tidak rusak saat di-parsing Gson
-keep class com.example.tamengtautan.** { *; }
-keep class com.siva.tamengtautan.** { *; }

# Aturan Pengecualian untuk Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.Unsafe
-keep class com.google.gson.** { *; }

# Aturan Pengecualian untuk Retrofit & OkHttp (Koneksi Supabase)
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Exceptions
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Aturan Pengecualian untuk Machine Learning ONNX Runtime
-dontwarn ai.onnxruntime.**
-keep class ai.onnxruntime.** { *; }