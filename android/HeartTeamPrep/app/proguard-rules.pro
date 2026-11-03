# OpenNLP loads model serializers, feature generators and extensions by class
# name via reflection, so R8 can't see those uses. Keep the library whole.
-keep class opennlp.** { *; }
-dontwarn org.osgi.**

# Optional TLS providers OkHttp (used by the Parse SDK) probes for at runtime.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# The Parse SDK registers its ParseObject subclasses and ParseCommand/decoder
# classes reflectively (needs their default constructors and field names).
-keep class com.parse.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
