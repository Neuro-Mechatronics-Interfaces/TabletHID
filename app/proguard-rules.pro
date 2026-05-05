# Preserve line numbers in stack traces uploaded to Play Console.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Bluetooth HID profile classes (accessed via reflection by the framework).
-keep class android.bluetooth.** { *; }

# Keep data/model classes used with JSON serialisation (SharedPreferences Codable-style).
-keep class com.tablet.hid.model.** { *; }

# Keep ViewModel subclasses (instantiated by ViewModelProvider reflection).
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Navigation Safe Args generated classes.
-keep class com.tablet.hid.**Args { *; }
-keep class com.tablet.hid.**Directions { *; }

# Material bottom sheet / dialog fragment (referenced by name in some paths).
-keep class com.google.android.material.bottomsheet.** { *; }