package be.supsecu.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import be.supsecu.app.core.AndroidIdnaCodec
import be.supsecu.app.core.Assessment
import be.supsecu.app.core.FraudAnalyzer
import be.supsecu.app.core.Intervention
import be.supsecu.app.core.UrlNormalizer
import be.supsecu.app.core.UrlSource
import be.supsecu.app.core.Verdict
import be.supsecu.app.notification.SecurityNotifier
import be.supsecu.app.reputation.ThreatFeedUpdater
import be.supsecu.app.reputation.ThreatRepository
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.Executors

class BrowserProtectionService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val backgroundExecutor = Executors.newFixedThreadPool(2)
    private val normalizer = UrlNormalizer(AndroidIdnaCodec)
    private val analyzer = FraudAnalyzer(normalizer)
    private val reader = BrowserPageReader(normalizer)
    private lateinit var notifier: SecurityNotifier
    private lateinit var overlay: SecurityAlertOverlay
    private lateinit var threatRepository: ThreatRepository
    private lateinit var visualScanner: BrandVisualScanner

    private var lastEvent: BrowserEventContext? = null
    private var candidateUrl: String? = null
    private var candidateFirstSeenAt = 0L
    private var activeAlertPackage: String? = null
    private var visualScanInFlight = false
    private var lastVisualScanUrl: String? = null
    private var lastVisualScanAt = 0L
    private val alertedKeys = mutableSetOf<String>()
    private val inspectRunnable = Runnable(::inspectActiveWindow)

    override fun onServiceConnected() {
        super.onServiceConnected()
        notifier = SecurityNotifier(this)
        overlay = SecurityAlertOverlay(this)
        threatRepository = ThreatRepository(this)
        visualScanner = BrandVisualScanner()
        notifier.createChannel()
        ThreatFeedUpdater(this, backgroundExecutor).refresh(force = false) { }
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

        val localAssessment = analyzer.analyze(
            rawUrl = snapshot.url,
            source = UrlSource.ADDRESS_BAR,
            signals = snapshot.signals,
        )
        val assessment = withThreatReputation(localAssessment)
        debug("assessment verdict=${assessment.verdict} brand=${assessment.brand?.id ?: "none"}")
        handleAssessment(assessment, snapshot.packageName)
        if (assessment.verdict == Verdict.NO_EVIDENCE && shouldRunVisualScan(snapshot)) {
            requestVisualScan(snapshot)
        }
    }

    private fun withThreatReputation(localAssessment: Assessment): Assessment {
        if (localAssessment.verdict == Verdict.OFFICIAL || localAssessment.verdict == Verdict.IMPERSONATION) {
            return localAssessment
        }
        val host = localAssessment.observedAsciiHost ?: return localAssessment
        val threat = threatRepository.assessHost(host) ?: return localAssessment
        val suspectedBrand = localAssessment.brand
        return if (suspectedBrand != null && threat.brand == null) {
            threat.copy(brand = suspectedBrand, verdict = Verdict.IMPERSONATION)
        } else {
            threat
        }
    }

    private fun shouldRunVisualScan(snapshot: BrowserSnapshot): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || visualScanInFlight) return false
        val now = SystemClock.elapsedRealtime()
        if (snapshot.url == lastVisualScanUrl && now - lastVisualScanAt < VISUAL_SCAN_COOLDOWN_MS) return false
        return snapshot.signals.any { signal ->
            signal.passwordField ||
                (signal.interactive && TRANSACTION_WORDS.any { word -> normalizeSignal(signal.text).contains(word) })
        }
    }

    private fun requestVisualScan(snapshot: BrowserSnapshot) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        visualScanInFlight = true
        lastVisualScanUrl = snapshot.url
        lastVisualScanAt = SystemClock.elapsedRealtime()
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            backgroundExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val visualResult = runCatching { visualScanner.scan(screenshot) }
                    handler.post {
                        visualScanInFlight = false
                        val visualSignals = visualResult.getOrElse { failure ->
                            debug("visual scan failed: ${failure.javaClass.simpleName}")
                            return@post
                        }
                        if (candidateUrl != snapshot.url || visualSignals.isEmpty()) return@post
                        val assessment = withThreatReputation(
                            analyzer.analyze(
                                rawUrl = snapshot.url,
                                source = UrlSource.ADDRESS_BAR,
                                signals = (snapshot.signals + visualSignals).take(600),
                            ),
                        )
                        debug("visual assessment verdict=${assessment.verdict} brand=${assessment.brand?.id ?: "none"}")
                        handleAssessment(assessment, snapshot.packageName)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    handler.post {
                        visualScanInFlight = false
                        debug("visual screenshot failed code=$errorCode")
                    }
                }
            },
        )
    }

    private fun normalizeSignal(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

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
                if (assessment.verdict == Verdict.KNOWN_THREAT) {
                    notifier.showKnownThreat(assessment)
                } else {
                    notifier.showImpersonation(assessment)
                }
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
        backgroundExecutor.shutdownNow()
        activeAlertPackage = null
        lastEvent = null
        super.onDestroy()
    }

    companion object {
        private const val EVENT_DEBOUNCE_MS = 300L
        private const val URL_STABILITY_MS = 500L
        private const val VISUAL_SCAN_COOLDOWN_MS = 10_000L
        private const val LOG_TAG = "SupSecuProtection"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private val TRANSACTION_WORDS = listOf(
            "acheter",
            "ajouter au panier",
            "commander",
            "paiement",
            "buy",
            "add to cart",
            "checkout",
            "bestellen",
            "winkelwagen",
            "in winkelmand",
            "se connecter",
            "sign in",
            "login",
        )
    }
}
