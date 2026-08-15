# AirMouse release ProGuard/R8 rules.
#
# R8 (minify) is enabled for release builds. These rules keep the pieces that
# are discovered reflectively or that must retain their structure for
# serialization, and strip verbose logging from the shipped APK.

# ---------------------------------------------------------------------------
# Release logging toggle: debug/verbose Log calls are compiled out of release
# builds entirely. Log.i/w/e (informational and error messages) are kept so
# production issues remain diagnosable.
# ---------------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static int d(java.lang.String, java.lang.String);
    public static int v(java.lang.String, java.lang.String);
    public static int d(java.lang.String, java.lang.String, java.lang.Throwable);
    public static int v(java.lang.String, java.lang.String, java.lang.Throwable);
}

# ---------------------------------------------------------------------------
# Stack traces: keep line numbers so crash logs are readable.
# ---------------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# ViewModels are instantiated reflectively by ViewModelProvider; keep their
# constructors (AndroidViewModel takes an Application, plain ones take none).
# ---------------------------------------------------------------------------
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(android.app.Application);
    <init>();
}

# ---------------------------------------------------------------------------
# Moshi: GesturePoint is serialized with a generated JsonAdapter. The whole
# gesture package is tiny, so keep it intact to cover the generated adapter,
# its factory, and the model regardless of how the adapter is discovered.
# ---------------------------------------------------------------------------
-keep class com.example.gesture.** { *; }
