# kotlinx.serialization (dùng trong module :core để lưu ván)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class vn.cotyphu.core.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class vn.cotyphu.core.**$$serializer { *; }
