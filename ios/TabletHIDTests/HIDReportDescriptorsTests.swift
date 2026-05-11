import XCTest
@testable import TabletHID

final class HIDReportDescriptorsTests: XCTestCase {
    func testMouseReportLayout() {
        let report = HIDReportDescriptors.buildMouseReport(
            buttons: 0b101,
            dx: 300,
            dy: -2,
            wheel: -1,
            horizontalWheel: 2
        )
        XCTAssertEqual(Array(report), [0x05, 0x2C, 0x01, 0xFE, 0xFF, 0xFF, 0x02])
    }

    func testGamepadReportLayout() {
        let buttons = (1 << HIDReportDescriptors.btnA) | (1 << HIDReportDescriptors.btnR3)
        let report = HIDReportDescriptors.buildGamepadReport(
            leftX: -32768,
            leftY: 32767,
            rightX: 258,
            rightY: -258,
            leftTrigger: 12,
            rightTrigger: 255,
            buttons: buttons,
            hat: HIDReportDescriptors.hatNW
        )
        XCTAssertEqual(Array(report), [
            0x00, 0x80,
            0xFF, 0x7F,
            0x0C, 0xFF,
            0x02, 0x01,
            0xFE, 0xFE,
            0x01, 0x02,
            0x07
        ])
    }

    func testKeyboardReportLayout() {
        let report = HIDReportDescriptors.buildKeyboardReport(
            modifiers: 0x1FF,
            keyUsages: [0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A]
        )
        XCTAssertEqual(Array(report), [
            0xFF, 0x00,
            0x04, 0x05, 0x06, 0x07, 0x08, 0x09
        ])
    }

    func testKeyboardReportPadsMissingUsages() {
        let report = HIDReportDescriptors.buildKeyboardReport(
            modifiers: 0x05,
            keyUsages: [0xE2, 0x28]
        )
        XCTAssertEqual(Array(report), [
            0x05, 0x00,
            0xE2, 0x28, 0x00, 0x00, 0x00, 0x00
        ])
    }

    func testCombinedDescriptorContainsBothReports() {
        XCTAssertEqual(
            HIDReportDescriptors.combinedReportDescriptor.count,
            HIDReportDescriptors.mouseReportDescriptor.count +
                HIDReportDescriptors.gamepadReportDescriptor.count +
                HIDReportDescriptors.keyboardReportDescriptor.count
        )
        XCTAssertTrue(HIDReportDescriptors.combinedReportDescriptor.containsSubsequence([0x85, HIDReportDescriptors.reportIDMouse]))
        XCTAssertTrue(HIDReportDescriptors.combinedReportDescriptor.containsSubsequence([0x85, HIDReportDescriptors.reportIDGamepad]))
        XCTAssertTrue(HIDReportDescriptors.combinedReportDescriptor.containsSubsequence([0x85, HIDReportDescriptors.reportIDKeyboard]))
        XCTAssertTrue(HIDReportDescriptors.combinedReportDescriptor.containsSubsequence([0x05, 0x01, 0x09, 0x06, 0xA1, 0x01, 0x85, 0x03]))
    }
}

private extension Array where Element == UInt8 {
    func containsSubsequence(_ values: [UInt8]) -> Bool {
        indices.contains { start in
            start + values.count <= count && Array(self[start..<(start + values.count)]) == values
        }
    }
}
