# R8 rules for the release (and bench) build.
#
# Libraries bring their own consumer rules, which cover everything this app uses:
# - kotlinx.serialization (@Serializable classes + generated serializers; all decoding goes through
#   explicit serializer() calls, no reflection lookup)
# - Media3 (ExoPlayer, HLS, OkHttp data source), OkHttp 5 / Okio, Coil 3, AndroidX, Compose.
# So no -keep rules are needed for them; add one here only with a failing release build to show for it.

# Readable stack traces in app.log / logcat from release builds (mapping.txt de-obfuscates names).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# @Serializable classes are decoded from JSON assets / network by their serial names: keep the
# generated serializers and companions of the app's models explicitly as a safety net.
-keepclassmembers @kotlinx.serialization.Serializable class com.bmiller.uzzu.** {
    static ** Companion;
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.bmiller.uzzu.**$$serializer { *; }
