package be.supsecu.app.service

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import be.supsecu.app.R
import be.supsecu.app.core.Assessment

class SecurityAlertOverlay(
    private val context: Context,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val inflater = LayoutInflater.from(context)
    private val handler = Handler(Looper.getMainLooper())
    private var currentView: View? = null
    private var resetConfirmation: Runnable? = null

    val isVisible: Boolean
        get() = currentView != null

    fun show(
        assessment: Assessment,
        onLeave: () -> Unit,
        onOpenOfficial: () -> Unit,
        onContinue: () -> Unit,
    ) {
        val brand = assessment.brand ?: return
        val observedHost = assessment.observedAsciiHost ?: return
        dismiss()

        val view = inflater.inflate(R.layout.view_security_alert, FrameLayout(context), false)
        view.findViewById<TextView>(R.id.alertIntro).text =
            view.context.getString(R.string.alert_intro, brand.displayName)
        view.findViewById<TextView>(R.id.alertObservedHost).text = observedHost
        view.findViewById<TextView>(R.id.alertOfficialLabel).text =
            view.context.getString(R.string.official_address_label, brand.displayName)
        view.findViewById<TextView>(R.id.alertOfficialUrl).text = brand.officialUrl

        view.findViewById<Button>(R.id.leaveSiteButton).setOnClickListener {
            dismiss()
            onLeave()
        }
        view.findViewById<Button>(R.id.openOfficialButton).apply {
            text = view.context.getString(R.string.open_named_official_site, brand.displayName)
            setOnClickListener {
                dismiss()
                onOpenOfficial()
            }
        }

        var awaitingConfirmation = false
        var confirmationStartedAt = 0L
        val continueButton = view.findViewById<Button>(R.id.continueButton)
        continueButton.setOnClickListener {
            val now = SystemClock.elapsedRealtime()
            debug("continue clicked awaiting=$awaitingConfirmation age=${now - confirmationStartedAt}")
            if (awaitingConfirmation && now - confirmationStartedAt >= MIN_CONFIRMATION_DELAY_MS) {
                dismiss()
                onContinue()
            } else if (!awaitingConfirmation) {
                awaitingConfirmation = true
                confirmationStartedAt = now
                continueButton.setText(R.string.continue_confirm)
                continueButton.setTextColor(view.context.getColor(R.color.danger))
                val reset = Runnable {
                    awaitingConfirmation = false
                    continueButton.setText(R.string.continue_risk)
                    continueButton.setTextColor(view.context.getColor(R.color.muted))
                }
                resetConfirmation = reset
                handler.postDelayed(reset, CONFIRMATION_TIMEOUT_MS)
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            0,
            PixelFormat.OPAQUE,
        )
        view.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE
        currentView = view
        windowManager.addView(view, params)
        view.post {
            view.findViewById<TextView>(R.id.alertTitle).apply {
                isFocusable = true
                requestFocus()
            }
        }
    }

    fun dismiss() {
        resetConfirmation?.let(handler::removeCallbacks)
        resetConfirmation = null
        currentView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        currentView = null
    }

    private fun debug(message: String) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Log.d(LOG_TAG, message)
        }
    }

    companion object {
        private const val CONFIRMATION_TIMEOUT_MS = 4_000L
        private const val MIN_CONFIRMATION_DELAY_MS = 600L
        private const val LOG_TAG = "SupSecuOverlay"
    }

}
