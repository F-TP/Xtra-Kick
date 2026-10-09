package com.xtrakick.app.util

import coil3.util.Logger

class DiagnosticCoilLogger(
    override var minLevel: Logger.Level = Logger.Level.Error,
) : Logger {

    override fun log(tag: String, level: Logger.Level, message: String?, throwable: Throwable?) {
        val text = message ?: throwable?.message ?: return
        when (level) {
            // Image CDN blips and network drops are expected noise; log to Logcat only
            // so they do not pollute user-facing diagnostic file exports.
            Logger.Level.Warn, Logger.Level.Error -> DiagnosticLogger.i(tag, text, throwable)
            else -> {}
        }
    }
}
