package com.example.documentsummarizer.utils
import android.util.Log

object Log {
    const val TAG = "Anh"

    inline fun d(msg: () -> String) = Log.d(TAG, msg())
    inline fun i(msg: () -> String) = Log.i(TAG, msg())
    inline fun w(msg: () -> String) = Log.w(TAG, msg())
    inline fun e(msg: () -> String) = Log.e(TAG, msg())

    inline fun e(t: Throwable, msg: () -> String = { t.message ?: "error" }) =
        Log.e(TAG, msg(), t)
}