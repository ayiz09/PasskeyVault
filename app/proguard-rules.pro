# kotpass parses XML reflectively-free, but keep its models intact to be safe.
-keep class app.keemobile.kotpass.** { *; }
-dontwarn org.xmlpull.v1.**
-dontwarn javax.xml.**
# XML builder/parser bundled inside kotpass.
-keep class org.redundent.kotlin.xml.** { *; }
