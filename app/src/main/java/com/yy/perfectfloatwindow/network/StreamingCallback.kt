package com.yy.perfectfloatwindow.network

interface StreamingCallback {
    fun onChunk(text: String)
    fun onThinkingStart() {}
    fun onThinkingChunk(text: String) {}
    fun onThinkingComplete() {}
    fun onComplete()
    fun onError(error: Exception)
}
