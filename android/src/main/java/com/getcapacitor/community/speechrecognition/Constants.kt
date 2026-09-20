package com.getcapacitor.community.speechrecognition

import android.Manifest

public object Constants {
    public const val REQUEST_CODE_PERMISSION: Int = 2001
    public const val REQUEST_CODE_SPEECH: Int = 2002
    public const val IS_RECOGNITION_AVAILABLE: String = "isRecognitionAvailable"
    public const val START_LISTENING: String = "startListening"
    public const val STOP_LISTENING: String = "stopListening"
    public const val GET_SUPPORTED_LANGUAGES: String = "getSupportedLanguages"
    public const val HAS_PERMISSION: String = "hasPermission"
    public const val REQUEST_PERMISSION: String = "requestPermission"
    public const val MAX_RESULTS: Int = 5
    public const val NOT_AVAILABLE: String = "Speech recognition service is not available."
    public const val MISSING_PERMISSION: String = "Missing permission"
    public const val RECORD_AUDIO_PERMISSION: String = Manifest.permission.RECORD_AUDIO
    public const val ERROR: String = "Could not get list of languages"
}
