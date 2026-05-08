package com.tablet.hid.bluetooth

/** HID report descriptor byte arrays and report-building helpers. */
object HidReportDescriptors {

    const val REPORT_ID_MOUSE: Byte = 0x01
    const val REPORT_ID_GAMEPAD: Byte = 0x02

    // Convenience so descriptor literals stay readable without repeated .toByte() noise.
    private val Int.b get() = toByte()

    /**
     * Standard relative-mouse descriptor.
     * Report ID 1 | 7 bytes: [buttons(1)] [X lo][X hi] [Y lo][Y hi] [vwheel] [hwheel/AC Pan]
     */
    val MOUSE_REPORT_DESCRIPTOR: ByteArray = byteArrayOf(
        0x05.b, 0x01,        // Usage Page (Generic Desktop)
        0x09.b, 0x02,        // Usage (Mouse)
        0xA1.b, 0x01,        // Collection (Application)
        0x85.b, 0x01,        //   Report ID (1)
        0x09.b, 0x01,        //   Usage (Pointer)
        0xA1.b, 0x00,        //   Collection (Physical)
        // --- Buttons (3 bits) ---
        0x05.b, 0x09,        //     Usage Page (Button)
        0x19.b, 0x01,        //     Usage Minimum (Button 1 – left)
        0x29.b, 0x03,        //     Usage Maximum (Button 3 – middle)
        0x15.b, 0x00,        //     Logical Minimum (0)
        0x25.b, 0x01,        //     Logical Maximum (1)
        0x75.b, 0x01,        //     Report Size (1 bit)
        0x95.b, 0x03,        //     Report Count (3)
        0x81.b, 0x02,        //     Input (Data, Variable, Absolute)
        // --- Padding (5 bits to fill byte) ---
        0x75.b, 0x05,        //     Report Size (5)
        0x95.b, 0x01,        //     Report Count (1)
        0x81.b, 0x03,        //     Input (Constant, Variable, Absolute)
        // --- X / Y axes (signed 16-bit relative) ---
        0x05.b, 0x01,        //     Usage Page (Generic Desktop)
        0x09.b, 0x30,        //     Usage (X)
        0x09.b, 0x31,        //     Usage (Y)
        0x16.b, 0x00.b, 0x80.b, // Logical Minimum (-32768)
        0x26.b, 0xFF.b, 0x7F.b, // Logical Maximum (32767)
        0x75.b, 0x10,        //     Report Size (16)
        0x95.b, 0x02,        //     Report Count (2)
        0x81.b, 0x06,        //     Input (Data, Variable, Relative)
        // --- Vertical scroll wheel (signed 8-bit relative) ---
        0x09.b, 0x38,        //     Usage (Wheel)
        0x15.b, 0x81.b,      //     Logical Minimum (-127)
        0x25.b, 0x7F,        //     Logical Maximum (127)
        0x75.b, 0x08,        //     Report Size (8)
        0x95.b, 0x01,        //     Report Count (1)
        0x81.b, 0x06,        //     Input (Data, Variable, Relative)
        // --- Horizontal scroll / AC Pan (signed 8-bit relative) ---
        0x05.b, 0x0C,           //     Usage Page (Consumer)
        0x0A.b, 0x38.b, 0x02.b, //     Usage (AC Pan, 0x0238)
        0x15.b, 0x81.b,         //     Logical Minimum (-127)
        0x25.b, 0x7F,           //     Logical Maximum (127)
        0x75.b, 0x08,           //     Report Size (8)
        0x95.b, 0x01,           //     Report Count (1)
        0x81.b, 0x06,           //     Input (Data, Variable, Relative)
        0xC0.b,              //   End Collection
        0xC0.b               // End Collection
    )

    /**
     * Xbox-style gamepad descriptor.
     * Report ID 2 | 13 bytes:
     *   [LSX lo][LSX hi][LSY lo][LSY hi]
     *   [LT][RT]
     *   [RSX lo][RSX hi][RSY lo][RSY hi]
     *   [btns lo][btns hi]  (10 button bits + 6 pad)
     *   [hat nibble | pad nibble]
     * Axis order X,Y → Z,Rz → Rx,Ry matches Xbox-standard for broadest host compatibility.
     */
    val GAMEPAD_REPORT_DESCRIPTOR: ByteArray = byteArrayOf(
        0x05.b, 0x01,        // Usage Page (Generic Desktop)
        0x09.b, 0x05,        // Usage (Game Pad)
        0xA1.b, 0x01,        // Collection (Application)
        0x85.b, 0x02,        //   Report ID (2)
        // --- Left stick (2 × signed 16-bit absolute) ---
        0x09.b, 0x30,        //   Usage (X)   — left stick X
        0x09.b, 0x31,        //   Usage (Y)   — left stick Y
        0x16.b, 0x00.b, 0x80.b, // Logical Minimum (-32768)
        0x26.b, 0xFF.b, 0x7F.b, // Logical Maximum (32767)
        0x75.b, 0x10,        //   Report Size (16)
        0x95.b, 0x02,        //   Report Count (2)
        0x81.b, 0x02,        //   Input (Data, Variable, Absolute)
        // --- Triggers (2 × unsigned 8-bit) --- Xbox-standard: Z/Rz between the stick pairs
        0x09.b, 0x32,        //   Usage (Z)  — left trigger
        0x09.b, 0x35,        //   Usage (Rz) — right trigger
        0x15.b, 0x00,        //   Logical Minimum (0)
        0x26.b, 0xFF.b, 0x00.b, // Logical Maximum (255)
        0x75.b, 0x08,        //   Report Size (8)
        0x95.b, 0x02,        //   Report Count (2)
        0x81.b, 0x02,        //   Input (Data, Variable, Absolute)
        // --- Right stick (2 × signed 16-bit absolute) ---
        0x09.b, 0x33,        //   Usage (Rx) — right stick X
        0x09.b, 0x34,        //   Usage (Ry) — right stick Y
        0x16.b, 0x00.b, 0x80.b, // Logical Minimum (-32768)
        0x26.b, 0xFF.b, 0x7F.b, // Logical Maximum (32767)
        0x75.b, 0x10,        //   Report Size (16)
        0x95.b, 0x02,        //   Report Count (2)
        0x81.b, 0x02,        //   Input (Data, Variable, Absolute)
        // --- Buttons (10 bits: A B X Y LB RB Back Start L3 R3) ---
        0x05.b, 0x09,        //   Usage Page (Button)
        0x19.b, 0x01,        //   Usage Minimum (Button 1)
        0x29.b, 0x0A,        //   Usage Maximum (Button 10)
        0x15.b, 0x00,        //   Logical Minimum (0)
        0x25.b, 0x01,        //   Logical Maximum (1)
        0x75.b, 0x01,        //   Report Size (1)
        0x95.b, 0x0A,        //   Report Count (10)
        0x81.b, 0x02,        //   Input (Data, Variable, Absolute)
        // --- Padding (6 bits) ---
        0x75.b, 0x06,        //   Report Size (6)
        0x95.b, 0x01,        //   Report Count (1)
        0x81.b, 0x03,        //   Input (Constant, Variable, Absolute)
        // --- D-pad / hat switch (4-bit with null state) ---
        0x05.b, 0x01,        //   Usage Page (Generic Desktop)
        0x09.b, 0x39,        //   Usage (Hat Switch)
        0x15.b, 0x00,        //   Logical Minimum (0)
        0x25.b, 0x07,        //   Logical Maximum (7)   0=N 1=NE 2=E … 7=NW; 8+=none
        0x35.b, 0x00,        //   Physical Minimum (0)
        0x46.b, 0x3B.b, 0x01.b, // Physical Maximum (315 degrees)
        0x65.b, 0x14,        //   Unit (Eng-Rotation: degrees)
        0x75.b, 0x04,        //   Report Size (4)
        0x95.b, 0x01,        //   Report Count (1)
        0x81.b, 0x42,        //   Input (Data, Variable, Absolute, Null State)
        // --- Padding (4 bits to fill byte) ---
        0x65.b, 0x00,        //   Unit (None)
        0x75.b, 0x04,        //   Report Size (4)
        0x95.b, 0x01,        //   Report Count (1)
        0x81.b, 0x03,        //   Input (Constant, Variable, Absolute)
        0xC0.b               // End Collection
    )

    /**
     * Combined mouse + gamepad descriptor — one bond, two HID application collections.
     * The host enumerates both a mouse device (Report ID 1) and a gamepad device (Report ID 2)
     * from this single Bluetooth pairing.
     */
    val COMBINED_REPORT_DESCRIPTOR: ByteArray =
        MOUSE_REPORT_DESCRIPTOR + GAMEPAD_REPORT_DESCRIPTOR

    // -------------------------------------------------------------------------
    // Report builders
    // -------------------------------------------------------------------------

    /** Build a 7-byte mouse report. dx/dy are signed 16-bit (-32768..32767). */
    fun buildMouseReport(buttons: Int, dx: Int, dy: Int, wheel: Int = 0, hwheel: Int = 0): ByteArray =
        ByteArray(7).also { r ->
            r[0] = (buttons and 0x07).toByte()
            r[1] = (dx and 0xFF).toByte()
            r[2] = ((dx shr 8) and 0xFF).toByte()
            r[3] = (dy and 0xFF).toByte()
            r[4] = ((dy shr 8) and 0xFF).toByte()
            r[5] = wheel.toByte()
            r[6] = hwheel.toByte()
        }

    /**
     * Build a 13-byte gamepad report.
     * stick values: -32768..32767; trigger: 0..255; hat: 0-7 = direction, 8 = centered.
     */
    fun buildGamepadReport(
        leftX: Int, leftY: Int,
        rightX: Int, rightY: Int,
        leftTrigger: Int, rightTrigger: Int,
        buttons: Int,
        hat: Int
    ): ByteArray = ByteArray(13).also { r ->
        // Layout mirrors descriptor: LSX, LSY, LT, RT, RSX, RSY, buttons, hat
        r[0] = (leftX and 0xFF).toByte()
        r[1] = ((leftX shr 8) and 0xFF).toByte()
        r[2] = (leftY and 0xFF).toByte()
        r[3] = ((leftY shr 8) and 0xFF).toByte()
        r[4] = (leftTrigger and 0xFF).toByte()
        r[5] = (rightTrigger and 0xFF).toByte()
        r[6] = (rightX and 0xFF).toByte()
        r[7] = ((rightX shr 8) and 0xFF).toByte()
        r[8] = (rightY and 0xFF).toByte()
        r[9] = ((rightY shr 8) and 0xFF).toByte()
        r[10] = (buttons and 0xFF).toByte()
        r[11] = ((buttons shr 8) and 0x03).toByte()   // bits 8-9 of 10-bit field
        r[12] = (hat and 0x0F).toByte()               // lower nibble; upper nibble = 0 padding
    }

    // Button bit indices for buildGamepadReport(buttons = …)
    const val BTN_A     = 0
    const val BTN_B     = 1
    const val BTN_X     = 2
    const val BTN_Y     = 3
    const val BTN_LB    = 4
    const val BTN_RB    = 5
    const val BTN_BACK  = 6
    const val BTN_START = 7
    const val BTN_L3    = 8
    const val BTN_R3    = 9

    // Hat-switch values
    const val HAT_N   = 0
    const val HAT_NE  = 1
    const val HAT_E   = 2
    const val HAT_SE  = 3
    const val HAT_S   = 4
    const val HAT_SW  = 5
    const val HAT_W   = 6
    const val HAT_NW  = 7
    const val HAT_NONE = 8
}
