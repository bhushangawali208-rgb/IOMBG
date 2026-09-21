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

# Retain line number information for production crash debugging
-keepattributes SourceFile,LineNumberTable

# Firebase & Firestore Models
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.example.data.model.** { *; }
-keepclassmembers class com.example.data.model.** { <fields>; <init>(...); }

# Kotlinx Serialization
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
    @kotlinx.serialization.SerialName <fields>;
}
-keep class kotlinx.serialization.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**


# AndroidX Credentials
-dontwarn androidx.credentials.provider.**
