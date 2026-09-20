package com.getcapacitor.community.speechrecognition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.PluginCall

public class Receiver(private val call: PluginCall) : BroadcastReceiver() {
    public var supportedLanguages: List<String>? = null
        private set

    public var languagePreference: String? = null
        private set

    override fun onReceive(context: Context?, intent: Intent?) {
        val extras = getResultExtras(true)

        if (extras.containsKey(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE)) {
            languagePreference = extras.getString(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE)
        }

        if (extras.containsKey(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES)) {
            supportedLanguages = extras.getStringArrayList(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES)

            val languagesList = JSArray(supportedLanguages)
            call.resolve(JSObject().put("languages", languagesList))
            return
        }

        call.reject(Constants.ERROR)
    }

    public companion object {
        public const val TAG: String = "Receiver"
    }
}
