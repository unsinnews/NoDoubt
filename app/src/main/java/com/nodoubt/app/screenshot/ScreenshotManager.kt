package com.nodoubt.app.screenshot

object ScreenshotManager {

    interface ScreenshotCallback {
        fun onScreenshotRequested()
    }

    private var callback: ScreenshotCallback? = null

    fun setCallback(callback: ScreenshotCallback?) {
        this.callback = callback
    }

    fun requestScreenshot() {
        if (ScreenshotService.isServiceRunning) {
            ScreenshotService.requestScreenshot()
        } else {
            callback?.onScreenshotRequested()
        }
    }
}
