package com.getcapacitor.community.speechrecognition

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.result.ActivityResult
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Logger
import com.getcapacitor.PermissionState
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import org.json.JSONArray

@CapacitorPlugin(
    permissions = [Permission(strings = [Manifest.permission.RECORD_AUDIO], alias = SpeechRecognition.SPEECH_RECOGNITION)]
)
public class SpeechRecognition : Plugin() {
    private var languageReceiver: Receiver? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private val lock = ReentrantLock()
    private var listening = false

    private var previousPartialResults = JSONArray()

    override fun load() {
        super.load()
        bridge.webView.post {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(bridge.activity)
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(SpeechRecognitionListener())
            Logger.info(logTag, "Instantiated SpeechRecognizer in load()")
        }
    }

    @PluginMethod
    public fun available(call: PluginCall) {
        Logger.info(logTag, "Called for available(): $isSpeechRecognitionAvailable")
        val result = JSObject()
        result.put("available", isSpeechRecognitionAvailable)
        call.resolve(result)
    }

    @PluginMethod
    public fun start(call: PluginCall) {
        if (!isSpeechRecognitionAvailable) {
            call.unavailable(Constants.NOT_AVAILABLE)
            return
        }

        if (getPermissionState(SPEECH_RECOGNITION) != PermissionState.GRANTED) {
            call.reject(Constants.MISSING_PERMISSION)
            return
        }

        val language = call.getString("language", Locale.getDefault().toString())
        val maxResults = call.getInt("maxResults", Constants.MAX_RESULTS) ?: Constants.MAX_RESULTS
        val prompt = call.getString("prompt", null)
        val partialResults = call.getBoolean("partialResults", false) ?: false
        val popup = call.getBoolean("popup", false) ?: false
        beginListening(language, maxResults, prompt, partialResults, popup, call)
    }

    @PluginMethod
    public fun stop(call: PluginCall) {
        try {
            stopListening()
        } catch (ex: Exception) {
            call.reject(ex.localizedMessage)
        }
    }

    @PluginMethod
    public fun getSupportedLanguages(call: PluginCall) {
        val receiver = languageReceiver ?: Receiver(call).also { languageReceiver = it }

        val supportedLanguages = receiver.supportedLanguages
        if (supportedLanguages != null) {
            val languages = JSONArray(supportedLanguages)
            call.resolve(JSObject().put("languages", languages))
            return
        }

        val detailsIntent = Intent(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS)
        detailsIntent.setPackage("com.google.android.googlequicksearchbox")
        bridge.activity.sendOrderedBroadcast(detailsIntent, null, receiver, null, Activity.RESULT_OK, null, null)
    }

    @PluginMethod
    public fun isListening(call: PluginCall) {
        call.resolve(JSObject().put("listening", listening))
    }

    @ActivityCallback
    private fun listeningResult(call: PluginCall?, result: ActivityResult) {
        if (call == null) {
            return
        }

        val resultCode = result.resultCode
        if (resultCode == Activity.RESULT_OK) {
            try {
                // A result without data ended up in the catch block in Java as well
                val matchesList = result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val resultObj = JSObject()
                resultObj.put("matches", JSArray(matchesList))
                call.resolve(resultObj)
            } catch (ex: Exception) {
                call.reject(ex.message)
            }
        } else {
            call.reject(resultCode.toString())
        }

        lock.lock()
        listening = false
        lock.unlock()
    }

    private val isSpeechRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(bridge.context)

    private fun beginListening(
        language: String?,
        maxResults: Int,
        prompt: String?,
        partialResults: Boolean,
        showPopup: Boolean,
        call: PluginCall
    ) {
        Logger.info(logTag, "Beginning to listen for audible speech")

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, maxResults)
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, bridge.activity.packageName)
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, partialResults)
        intent.putExtra("android.speech.extra.DICTATION_MODE", partialResults)

        if (prompt != null) {
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }

        if (showPopup) {
            startActivityForResult(call, intent, "listeningResult")
        } else {
            bridge.webView.post {
                try {
                    lock.lock()

                    speechRecognizer?.let {
                        it.cancel()
                        it.destroy()
                        speechRecognizer = null
                    }

                    val recognizer = SpeechRecognizer.createSpeechRecognizer(bridge.activity)
                    speechRecognizer = recognizer
                    val listener = SpeechRecognitionListener()
                    listener.call = call
                    listener.partialResults = partialResults
                    recognizer.setRecognitionListener(listener)
                    recognizer.startListening(intent)
                    listening = true
                    if (partialResults) {
                        call.resolve()
                    }
                } catch (ex: Exception) {
                    call.reject(ex.message)
                } finally {
                    lock.unlock()
                }
            }
        }
    }

    private fun stopListening() {
        bridge.webView.post {
            try {
                lock.lock()
                if (listening) {
                    // Java dereferenced the recognizer without a check here as well
                    speechRecognizer!!.stopListening()
                    listening = false
                }
            } finally {
                lock.unlock()
            }
        }
    }

    private inner class SpeechRecognitionListener : RecognitionListener {
        var call: PluginCall? = null
        var partialResults: Boolean = false

        override fun onReadyForSpeech(params: Bundle?) {}

        override fun onBeginningOfSpeech() {
            try {
                lock.lock()
                // Notify listeners that recording has started
                val ret = JSObject()
                ret.put("status", "started")
                notifyListeners(LISTENING_EVENT, ret)
            } finally {
                lock.unlock()
            }
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            bridge.webView.post {
                try {
                    lock.lock()
                    listening = false

                    val ret = JSObject()
                    ret.put("status", "stopped")
                    notifyListeners(LISTENING_EVENT, ret)
                } finally {
                    lock.unlock()
                }
            }
        }

        override fun onError(error: Int) {
            stopListening()
            val errorMssg = getErrorText(error)

            call?.reject(errorMssg)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

            try {
                val jsArray = JSArray(matches)

                call?.let { call ->
                    if (!partialResults) {
                        call.resolve(JSObject().put("status", "success").put("matches", jsArray))
                    } else {
                        val ret = JSObject()
                        ret.put("matches", jsArray)
                        notifyListeners("partialResults", ret)
                    }
                }
            } catch (ex: Exception) {
                call?.resolve(JSObject().put("status", "error").put("message", ex.message))
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val matchesJSON = JSArray(matches)

            try {
                if (!matches.isNullOrEmpty() && previousPartialResults != matchesJSON) {
                    previousPartialResults = matchesJSON
                    val ret = JSObject()
                    ret.put("matches", previousPartialResults)
                    notifyListeners("partialResults", ret)
                }
            } catch (ex: Exception) {
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun getErrorText(errorCode: Int): String = when (errorCode) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
        SpeechRecognizer.ERROR_SERVER -> "error from server"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
        else -> "Didn't understand, please try again."
    }

    public companion object {
        public const val TAG: String = "SpeechRecognition"
        private const val LISTENING_EVENT = "listeningState"
        internal const val SPEECH_RECOGNITION: String = "speechRecognition"
    }
}
