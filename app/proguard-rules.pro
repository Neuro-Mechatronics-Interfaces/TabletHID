# Preserve line numbers in stack traces uploaded to Play Console.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Bluetooth HID profile classes (accessed via reflection by the framework).
-keep class android.bluetooth.** { *; }

# Keep our HID manager and its anonymous Callback subclass — the BT framework
# invokes onAppStatusChanged/onConnectionStateChanged etc. from the system side,
# which R8 cannot trace, so these methods must not be renamed or removed.
-keep class com.tablet.hid.bluetooth.** { *; }

# Explicitly keep all BT profile subclasses and their override methods.
# R8 can devirtualize callbacks that only have one concrete subclass visible to it,
# causing the system's virtual dispatch to fall through to the empty parent impl.
-keep class * extends android.bluetooth.BluetoothHidDevice$Callback { *; }
-keep class * extends android.bluetooth.BluetoothProfile$ServiceListener { *; }

# Keep data/model classes used with JSON serialisation (SharedPreferences Codable-style).
-keep class com.tablet.hid.model.** { *; }

# Keep ViewModel subclasses (instantiated by ViewModelProvider reflection).
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Navigation Safe Args generated classes.
-keep class com.tablet.hid.**Args { *; }
-keep class com.tablet.hid.**Directions { *; }

# Material bottom sheet / dialog fragment (referenced by name in some paths).
-keep class com.google.android.material.bottomsheet.** { *; }