# NexBrowser R8 rules.

# Keep annotations/generics used by reflection & Compose.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Keep app entry points.
-keep class nx.browser.** { *; }

# NewPipeExtractor uses Rhino (JS interpreter) for YouTube signature deciphering.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn org.w3c.dom.**
-dontwarn org.xml.sax.**
-dontwarn java.awt.**
-dontwarn org.jsoup.**
-dontwarn org.json.**
-dontwarn com.google.protobuf.**

# Strip debug/logging noise in release.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
