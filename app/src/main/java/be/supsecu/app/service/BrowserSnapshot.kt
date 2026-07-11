package be.supsecu.app.service

import be.supsecu.app.core.AccessibleSignal

data class BrowserSnapshot(
    val url: String,
    val packageName: String,
    val windowId: Int,
    val signals: List<AccessibleSignal>,
)

data class BrowserEventContext(
    val eventType: Int,
    val windowId: Int,
    val title: String?,
)
