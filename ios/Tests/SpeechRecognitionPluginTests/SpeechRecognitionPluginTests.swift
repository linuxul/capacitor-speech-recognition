import XCTest
import Capacitor
@testable import SpeechRecognitionPlugin

class PluginTests: XCTestCase {
    func testBridgedPlugin() {
        let plugin = SpeechRecognition()

        XCTAssertEqual("SpeechRecognition", plugin.identifier)
        XCTAssertEqual("SpeechRecognition", plugin.jsName)
        XCTAssertEqual(
            [
                "available", "start", "stop", "getSupportedLanguages", "hasPermission", "isListening",
                "requestPermission", "checkPermissions", "requestPermissions", "removeAllListeners"
            ],
            plugin.pluginMethods.map { $0.name })
    }

    func testIsListeningBeforeStart() {
        let plugin = SpeechRecognition()
        let resolved = expectation(description: "isListening resolves")

        let call = CAPPluginCall(callbackId: "test", methodName: "isListening", options: [:], success: { result, _ in
            XCTAssertEqual(false, result.data?["listening"] as? Bool)
            resolved.fulfill()
        }, error: { _ in
            XCTFail("Error shouldn't have been called")
        })

        plugin.isListening(call)
        wait(for: [resolved], timeout: 1)
    }
}
