# Sahaayika ProGuard / R8 Rules

# Keep Kotlin Serialization classes and serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,allowobfuscation,allowshrinking class * {
    <init>(...);
}
-keep class com.sahaayika.app.data.model.** { *; }
-keep class com.sahaayika.app.privacy.** { *; }

# Keep Custom Views so XML inflation works cleanly
-keep class com.sahaayika.app.ui.avatar.FloatingDidiAvatarView { *; }
-keep class com.sahaayika.app.ui.overlay.SpotlightCanvasView { *; }
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep OkHttp & Okio internals
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
