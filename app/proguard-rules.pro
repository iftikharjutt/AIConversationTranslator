# AI Conversation Translator ProGuard Rules
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
