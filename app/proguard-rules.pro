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

# BleHidManager uses anonymous/inner subclasses of these BLE callbacks; the OS
# dispatches into them via reflection so R8 must not rename or remove their methods.
-keep class * extends android.bluetooth.BluetoothGattServerCallback { *; }
-keep class * extends android.bluetooth.le.AdvertiseCallback { *; }

# HidReportDescriptors — object with descriptor byte arrays and report builder
# methods (buildMouseReport, buildGamepadReport).  Called directly from production
# code and from unit tests; R8 cannot see the test call-sites in a release build.
-keep class com.tablet.hid.bluetooth.HidReportDescriptors {
    public static final byte[] MOUSE_REPORT_DESCRIPTOR;
    public static final byte[] GAMEPAD_REPORT_DESCRIPTOR;
    public static final byte[] COMBINED_REPORT_DESCRIPTOR;
    public static byte[] buildMouseReport(...);
    public static byte[] buildGamepadReport(...);
}

# Keep data/model classes used with JSON serialisation (SharedPreferences Codable-style).
-keep class com.tablet.hid.model.** { *; }

# Enums serialised by name() to SharedPreferences (TriggerDragAxis, ClickBehavior,
# TouchMode, ZoneType).  Preserve name(), ordinal(), and valueOf() so round-trips work.
-keepclassmembers enum com.tablet.hid.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public final java.lang.String name();
    public final int ordinal();
}

# Data classes persisted to SharedPreferences use their property names as keys.
# Keep componentN() and copy() so destructuring and copy-with-defaults still work
# after minification (R8 may strip them when no Kotlin reflection is present).
-keepclassmembers class com.tablet.hid.model.** {
    public ** component*();
    public ** copy(...);
}

# Keep ViewModel subclasses (instantiated by ViewModelProvider reflection).
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Navigation Safe Args generated classes.
-keep class com.tablet.hid.**Args { *; }
-keep class com.tablet.hid.**Directions { *; }

# Material bottom sheet / dialog fragment (referenced by name in some paths).
-keep class com.google.android.material.bottomsheet.** { *; }

# BuildConfig is referenced by name in several places; keep it so that DEV_MODE
# and other generated fields survive minification.
-keep class com.tablet.hid.BuildConfig { *; }

# Kotlin coroutines — keep the debug-agent class and the internal machinery that
# R8 must not inline away (coroutine state machines use reflection for stack traces).
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
