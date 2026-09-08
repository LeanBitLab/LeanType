# ============================================================
# JNI / Native Code Protection — LeanType Keyboard
# ============================================================

# 1. Keep ALL native method declarations globally
-keepclasseswithmembernames class * {
    native <methods>;
}

# 2. Dynamic native registration via RegisterNatives
-keep class com.android.inputmethod.latin.BinaryDictionary { *; }
-keep class com.android.inputmethod.latin.BinaryDictionary$Companion { *; }
-keep class com.android.inputmethod.latin.utils.BinaryDictionaryUtils { *; }
-keep class com.android.inputmethod.latin.utils.BinaryDictionaryUtils$Companion { *; }
-keep class com.android.inputmethod.latin.DicTraverseSession { *; }
-keep class com.android.inputmethod.latin.DicTraverseSession$Companion { *; }
-keep class com.android.inputmethod.keyboard.ProximityInfo { *; }
-keep class com.android.inputmethod.keyboard.ProximityInfo$Companion { *; }

# 3. JNI field reflection via GetFieldID
-keep class com.android.inputmethod.latin.utils.WordInputEventForPersonalization {
    <fields>;
    <init>(...);
}

# 4. JNI library loader
-keep class helium314.keyboard.latin.utils.JniUtils { *; }

# 5. Native method parameter types to preserve method signatures
-keep class helium314.keyboard.latin.dictionary.Dictionary { *; }
-keep class helium314.keyboard.latin.NgramContext { *; }
-keep class helium314.keyboard.latin.NgramContext$WordInfo { *; }
-keep class helium314.keyboard.latin.makedict.ProbabilityInfo { *; }

# 6. Enum methods values() and valueOf()
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 7. Parcelable CREATORs & Serializable
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 8. Kotlin metadata, @JvmField, and @JvmStatic native
-keep class kotlin.Metadata { *; }
-keepclassmembers class * {
    @kotlin.jvm.JvmField <fields>;
}
-keepclassmembers class * {
    @kotlin.jvm.JvmStatic native <methods>;
}
-dontwarn kotlinx.coroutines.**

# after upgrading to gradle 8, stack traces contain "unknown source"
-keepattributes SourceFile,LineNumberTable
-dontobfuscate

# Gemini SDK dependencies
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi

# Keep Gemini API classes
-keep class com.google.ai.client.generativeai.** { *; }

-keep class helium314.keyboard.latin.utils.ProofreadHelper { *; }
-keep class helium314.keyboard.latin.utils.ProofreadHelper$* { *; }

# Keep java-llama.cpp classes
-keep class de.kherud.llama.** { *; }
-keep class org.nehuatl.llamacpp.** { *; }



# Fix correct service name
-keep class helium314.keyboard.latin.utils.ProofreadService { *; }

# Suppress warnings for missing library dependencies in R8 Full Mode
-dontwarn com.google.api.client.**
-dontwarn java.lang.management.**
-dontwarn org.joda.time.**
-dontwarn com.google.ai.client.generativeai.**
-dontwarn de.kherud.llama.**
-dontwarn org.nehuatl.llamacpp.**

# Keep offline voice plugin AIDL interface, parcelables, and host managers
-keep class com.leanbitlab.leantype.voice.** { *; }
-keep interface com.leanbitlab.leantype.voice.** { *; }
-keep class helium314.keyboard.latin.voice.** { *; }

# Keep handwriting plugin interface and classes to prevent signature optimization or inlining
-keep interface helium314.keyboard.latin.handwriting.HandwritingRecognizer {
    <methods>;
}
-keep interface helium314.keyboard.latin.handwriting.ModelDownloadListener {
    <methods>;
}
-keep class helium314.keyboard.latin.handwriting.** { *; }
-keep interface helium314.keyboard.latin.handwriting.** { *; }

# Keep translation plugin interface to prevent parameter removal/signature optimization
-keep interface helium314.keyboard.latin.translation.ITranslationProvider {
    <methods>;
}
-keep interface helium314.keyboard.latin.translation.TranslationModelDownloadListener {
    <methods>;
}
-keep class helium314.keyboard.latin.translation.** { *; }
-keep interface helium314.keyboard.latin.translation.** { *; }

# Keep offline AI plugin interface to prevent parameter removal/signature optimization
-keep interface helium314.keyboard.latin.ai.IOfflineAiProvider {
    <methods>;
}
-keep class helium314.keyboard.latin.ai.** { *; }
-keep interface helium314.keyboard.latin.ai.** { *; }

# Keep OCR plugin interface and classes to prevent parameter removal or signature optimization
-keep interface helium314.keyboard.latin.ocr.ITextRecognizer {
    <methods>;
}
-keep class helium314.keyboard.latin.ocr.** { *; }
-keep interface helium314.keyboard.latin.ocr.** { *; }

# Keep WorkManager plugin factory & runtime for dynamically loaded plugins
-keep class helium314.keyboard.latin.work.** { *; }
-keep interface helium314.keyboard.latin.work.** { *; }
-keep class androidx.work.** { *; }
-keep interface androidx.work.** { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepnames class com.google.mlkit.** extends androidx.work.ListenableWorker
-dontwarn androidx.work.**

# Keep ML Kit, DataTransport, GMS Tasks, and Firebase components for plugin dynamic linkage
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.datatransport.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.google.firebase.** { *; }

# Keep Kotlin standard library for dynamically loaded plugins
# ponytail: keep kotlin stdlib classes to prevent NoSuchMethodError in plugin loading
-keep class kotlin.** { *; }

