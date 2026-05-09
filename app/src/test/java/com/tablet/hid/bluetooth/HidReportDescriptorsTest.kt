package com.tablet.hid.bluetooth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HidReportDescriptorsTest {

    @Test
    fun buildMouseReport_keepsSevenByteShape() {
        val report = HidReportDescriptors.buildMouseReport(
            buttons = 0xFF,
            dx = 0x1234,
            dy = -2,
            wheel = -1,
            hwheel = -128,
        )

        assertArrayEquals(
            byteArrayOf(
                0x07,
                0x34,
                0x12,
                0xFE.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0x80.toByte(),
            ),
            report,
        )
    }

    @Test
    fun buildGamepadReport_keepsThirteenByteShape() {
        val report = HidReportDescriptors.buildGamepadReport(
            leftX = -32768,
            leftY = 32767,
            rightX = -1,
            rightY = 0x1234,
            leftTrigger = 0x100,
            rightTrigger = 0x7F,
            buttons = 0xFFFF,
            hat = HidReportDescriptors.HAT_NONE,
        )

        assertArrayEquals(
            byteArrayOf(
                0x00,
                0x80.toByte(),
                0xFF.toByte(),
                0x7F,
                0x00,
                0x7F,
                0xFF.toByte(),
                0xFF.toByte(),
                0x34,
                0x12,
                0xFF.toByte(),
                0x03,
                0x08,
            ),
            report,
        )
    }

    @Test
    fun buildKeyboardReport_usesModifiersAndFirstSixUsages() {
        val report = HidReportDescriptors.buildKeyboardReport(
            modifiers = 0x1FF,
            keyUsages = listOf(0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A),
        )

        assertArrayEquals(
            byteArrayOf(
                0xFF.toByte(),
                0x00,
                0x04,
                0x05,
                0x06,
                0x07,
                0x08,
                0x09,
            ),
            report,
        )
    }

    @Test
    fun buildKeyboardReport_padsMissingUsagesWithZero() {
        val report = HidReportDescriptors.buildKeyboardReport(
            modifiers = 0x05,
            keyUsages = listOf(0xE2, 0x28),
        )

        assertArrayEquals(
            byteArrayOf(
                0x05,
                0x00,
                0xE2.toByte(),
                0x28,
                0x00,
                0x00,
                0x00,
                0x00,
            ),
            report,
        )
    }

    @Test
    fun combinedDescriptor_containsMouseGamepadAndKeyboardReportIds() {
        val descriptor = HidReportDescriptors.COMBINED_REPORT_DESCRIPTOR

        assertEquals(
            HidReportDescriptors.MOUSE_REPORT_DESCRIPTOR.size +
                HidReportDescriptors.GAMEPAD_REPORT_DESCRIPTOR.size +
                HidReportDescriptors.KEYBOARD_REPORT_DESCRIPTOR.size,
            descriptor.size,
        )
        assertTrue(descriptor.containsSubsequence(0x85, HidReportDescriptors.REPORT_ID_MOUSE.toInt()))
        assertTrue(descriptor.containsSubsequence(0x85, HidReportDescriptors.REPORT_ID_GAMEPAD.toInt()))
        assertTrue(descriptor.containsSubsequence(0x85, HidReportDescriptors.REPORT_ID_KEYBOARD.toInt()))
        assertTrue(descriptor.containsSubsequence(0x05, 0x01, 0x09, 0x06, 0xA1, 0x01, 0x85, 0x03))
    }

    private fun ByteArray.containsSubsequence(vararg values: Int): Boolean {
        val needle = values.map { (it and 0xFF).toByte() }.toByteArray()
        return indices.any { start ->
            start + needle.size <= size &&
                needle.indices.all { offset -> this[start + offset] == needle[offset] }
        }
    }
}
