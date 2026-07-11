package be.supsecu.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import be.supsecu.app.core.AndroidIdnaCodec
import be.supsecu.app.core.Assessment
import be.supsecu.app.core.FraudAnalyzer
import be.supsecu.app.core.Intervention
import be.supsecu.app.core.UrlNormalizer
import be.supsecu.app.core.UrlSource
import be.supsecu.app.core.Verdict
import be.supsecu.app.notification.SecurityNotifier

class BrowserProtectionService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val normalizer = UrlNormalizer(AndroidIdnaCodec)
    private val analyzer = FraudAnalyzer(normalizer)
    private val reader = BrowserPageReader(normalizer)
    private lateinit var notifier: SecurityNotifier
    private lateinit var overlay: SecurityAlertOverlay

    private var lastEvent: BrowserEventContext? = null
    private var candidateUrl: String? = null
    private var candidateFirstSeenAt = 0L
    private var activeAlertPackage: String? = null
    private val alertedKeys = mutableSetOf<String>()
    private val inspectRunnable = Runnable(::inspectActiveWindow)

    override fun onServiceConnected() {
        super.onServiceConnected()
        notifier = SecurityNotifier(this)
        overlay = SecurityAlertOverlay(this)
        notifier.createChannel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::overlay.isInitialized) return
        val eventPackage = event.packageName?.toString() ?: return
        if (eventPackage == packageName) return
        debug("event type=${event.eventType} package=$eventPackage overlay=${overlay.isVisible}")
        if (overlay.isVisible) {
            val windowChanged = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
            if (windowChanged && eventPackage != SYSTEM_UI_PACKAGE && eventPackage != activeAlertPackage) {
                overlay.dismiss()
                notifier.cancelAlerts()
                activeAlertPackage = null
            }
            return
        }

        lastEvent = BrowserEventContext(
            eventType = event.eventType,
            windowId = event.windowId,
            title = event.text.firstOrNull()?.toString(),
        )
        handler.removeCallbacks(inspectRunnable)
        handler.postDelayed(inspectRunnable, EVENT_DEBOUNCE_MS)
    }

    private fun inspectActiveWindow() {
        val root = rootInActiveWindow ?: run {
            debug("inspection skipped: no active root")
            return
        }
        val eventForWindow = lastEvent?.takeIf { it.windowId == root.windowId }
        val snapshot = reader.read(root, eventForWindow) ?: run {
            debug("inspection skipped: no browser snapshot for ${root.packageName}")
            return
        }
        if (snapshot.packageName == packageName) return
        debug("snapshot package=${snapshot.packageName} signals=${snapshot.signals.size}")

        val now = SystemClock.elapsedRealtime()
        if (snapshot.url != candidateUrl) {
            candidateUrl = snapshot.url
            candidateFirstSeenAt = now
            handler.postDelayed(inspectRunnable, URL_STABILITY_MS)
            return
        }
        val age = now - candidateFirstSeenAt
        if (age < URL_STABILITY_MS) {
            handler.postDelayed(inspectRunnable, URL_STABILITY_MS - age)
            return
        }

        val assessment = analyzer.analyze(
            rawUrl = snapshot.url,
            source = UrlSource.ADDRESS_BAR,
            signals = snapshot.signals,
        )
        debug("assessment verdict=${assessment.verdict} brand=${assessment.brand?.id ?: "none"}")
        handleAssessment(assessment, snapshot.packageName)
    }

    private fun handleAssessment(assessment: Assessment, sourcePackage: String) {
        if (assessment.verdict == Verdict.OFFICIAL) {
            notifier.cancelAlerts()
            overlay.dismiss()
            activeAlertPackage = null
            return
        }

        val key = "${assessment.observedAsciiHost}|${assessment.brand?.id}|${assessment.verdict}"
        if (!alertedKeys.add(key)) return

        when (assessment.intervention) {
            Intervention.FULL_SCREEN -> {
                activeAlertPackage = sourcePackage
                notifier.showImpersonation(assessment)
                overlay.show(
                    assessment = assessment,
                    onLeave = {
                        activeAlertPackage = null
                        notifier.cancelAlerts()
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    },
                    onOpenOfficial = {
                        activeAlertPackage = null
                        notifier.cancelAlerts()
                        openOfficialUrl(assessment.officialUrl)
                    },
                    onContinue = {
                        activeAlertPackage = null
                        // L’alerte reste supprimée pour ce couple domaine/marque pendant cette session.
                    },
                )
            }

            Intervention.NOTIFICATION -> notifier.showSuspicious(assessment)
            Intervention.NONE -> Unit
        }
    }

    private fun openOfficialUrl(url: String?) {
        if (url == null) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }
    }

    private fun debug(message: String) {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Log.d(LOG_TAG, message)
        }
    }

    override fun onInterrupt() {
        handler.removeCallbacks(inspectRunnable)
        debug("accessibility feedback interrupted")
    }

    override fun onDestroy() {
        handler.removeCallbacks(inspectRunnable)
        if (::overlay.isInitialized) overlay.dismiss()
        activeAlertPackage = null
        lastEvent = null
        super.onDestroy()
    }

    companion object {
        private const val EVENT_DEBOUNCE_MS = 300L
        private const val URL_STABILITY_MS = 500L
        private const val LOG_TAG = "SupSecuProtection"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }
}
