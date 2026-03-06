package com.kilu.pocketagent.core.utils

import android.util.Log

interface AppLogger {
    fun d(tag: String, message: String)
    fun e(tag: String, message: String, t: Throwable? = null)
}

object AndroidLogger : AppLogger {
    override fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun e(tag: String, message: String, t: Throwable?) {
        Log.e(tag, message, t)
    }
}

object NoopLogger : AppLogger {
    override fun d(tag: String, message: String) {}
    override fun e(tag: String, message: String, t: Throwable?) {}
}

object PrintLogger : AppLogger {
    override fun d(tag: String, message: String) {
        println("[$tag] D: $message")
    }

    override fun e(tag: String, message: String, t: Throwable?) {
        System.err.println("[$tag] E: $message")
        t?.printStackTrace()
    }
}
