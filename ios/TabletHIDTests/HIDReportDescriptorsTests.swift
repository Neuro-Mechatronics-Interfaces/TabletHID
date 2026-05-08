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

    func testCombinedDescriptorContainsBothReports() {
        XCTAssertEqual(HIDReportDescriptors.combinedReportDescriptor.count, HIDReportDescriptors.mouseReportDescriptor.count + HIDReportDescriptors.gamepadReportDescriptor.count)
        XCTAssertTrue(HIDReportDescriptors.combinedReportDescriptor.contains(HIDReportDescriptors.reportIDMouse))
        XCTAssertTrue(HIDReportDescriptors.combinedReportDescriptor.contains(HIDReportDescriptors.reportIDGamepad))
    }
}
