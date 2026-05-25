# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /sdk/tools/proguard/proguard-android.txt

# Keep Room entities
-keep class com.victor.ncfdoorcard.data.** { *; }

# Keep NFC related classes
-keep class com.victor.ncfdoorcard.NfcCardEmulationService { *; }
