import XCTest
import Capacitor
import Speech
@testable import SpeechRecognitionPlugin

class PluginTests: XCTestCase {
    func testBridgedPlugin() {
        let plugin = SpeechRecognition()

        XCTAssertEqual("SpeechRecognition", plugin.identifier)
        XCTAssertEqual("SpeechRecognition", plugin.jsName)
        XCTAssertEqual(
            ["available", "start", "stop", "getSupportedLanguages", "isListening", "checkPermissions", "requestPermissions"],
            plugin.pluginMethods.map { $0.name })
        XCTAssertEqual(
            ["promise", "promise", "promise", "promise", "promise", "promise", "promise"],
            plugin.pluginMethods.map { $0.returnType.rawValue })
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

    func testStartWithoutPermissionThrows() throws {
        try XCTSkipIf(SFSpeechRecognizer.authorizationStatus() == .authorized, "speech recognition is authorized")
        let plugin = SpeechRecognition()
        let call = CAPPluginCall(callbackId: "test", methodName: "start", options: [:], success: { _, _ in
            XCTFail("start must not resolve")
        }, error: { _ in
            XCTFail("start answers by throwing")
        })

        XCTAssertThrowsError(try plugin.start(call)) { error in
            // The bridge rejects the call with this message and no code, as the method did before.
            XCTAssertEqual((error as? CAPPluginError)?.message, "Missing permission")
            XCTAssertNil((error as? CAPPluginError)?.code)
        }
    }

    func testCheckPermissionsReportsTheAuthorizationStatus() {
        let plugin = SpeechRecognition()
        var permission: String?
        plugin.checkPermissions(CAPPluginCall(callbackId: "test", methodName: "checkPermissions", options: [:], success: { result, _ in
            permission = result.data?["speechRecognition"] as? String
        }, error: { _ in
            XCTFail("checkPermissions must not reject")
        }))

        switch SFSpeechRecognizer.authorizationStatus() {
        case .authorized:
            XCTAssertEqual(permission, "granted")
        case .denied, .restricted:
            XCTAssertEqual(permission, "denied")
        default:
            XCTAssertEqual(permission, "prompt")
        }
    }
}
