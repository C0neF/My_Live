# ============================================================================
# My Live - ProGuard/R8 Rules
# ============================================================================

# ── General ──────────────────────────────────────────────────────────────────
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# ── OkHttp / Okio ─────────────────────────────────────────────────────────────
# These -dontwarn rules are required by OkHttp/Okio (not Retrofit), which reference
# compile-only annotations absent at runtime.
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit

# ── Gson ─────────────────────────────────────────────────────────────────────
# Gson 2.11+ ships its own consumer rules; keep only custom TypeAdapters
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ── Room ─────────────────────────────────────────────────────────────────────
# Room 2.8+ ships consumer rules; keep entity/dao annotations for safety
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# ── kotlinx.serialization ────────────────────────────────────────────────────
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.mylive.app.core.model.**$$serializer { *; }
-keepclassmembers class com.mylive.app.core.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.mylive.app.core.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Data models used for JSON serialization ──────────────────────────────────
-keep class com.mylive.app.core.model.** { *; }
-keep class com.mylive.app.data.local.entity.** { *; }
-keep class com.mylive.app.core.site.huya.tars.model.** { *; }

# ── NanoHTTPD ────────────────────────────────────────────────────────────────
-keep class fi.iki.elonen.NanoHTTPD { *; }
-keep class fi.iki.elonen.NanoHTTPD$* { *; }

# ── QuickJS ──────────────────────────────────────────────────────────────────
-keep class com.quickjs.** { *; }

# ── Brotli ───────────────────────────────────────────────────────────────────
-keep class org.brotli.dec.** { *; }
