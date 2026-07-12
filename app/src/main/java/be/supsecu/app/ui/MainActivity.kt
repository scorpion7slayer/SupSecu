package be.supsecu.app.ui

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.view.accessibility.AccessibilityManager
import be.supsecu.app.R
import be.supsecu.app.core.AndroidIdnaCodec
import be.supsecu.app.core.Assessment
import be.supsecu.app.core.BrandCatalog
import be.supsecu.app.core.FraudAnalyzer
import be.supsecu.app.core.UrlNormalizer
import be.supsecu.app.core.UrlSource
import be.supsecu.app.core.Verdict
import be.supsecu.app.feedback.FeedbackConfig
import be.supsecu.app.service.BrowserProtectionService
import be.supsecu.app.reputation.ThreatFeedStats
import be.supsecu.app.reputation.ThreatFeedUpdater
import be.supsecu.app.reputation.ThreatRefreshResult
import be.supsecu.app.reputation.ThreatRepository
import be.supsecu.app.update.ApkUpdateInstaller
import be.supsecu.app.update.GitHubUpdateClient
import be.supsecu.app.update.UpdateCheckResult
import be.supsecu.app.update.UpdateConfig
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val normalizer = UrlNormalizer(AndroidIdnaCodec)
    private val analyzer = FraudAnalyzer(normalizer)
    private val updateExecutor = Executors.newSingleThreadExecutor()
    private val updateInstaller by lazy { ApkUpdateInstaller(this) }
    private val updatePreferences by lazy { getSharedPreferences("update_state", MODE_PRIVATE) }
    private val threatRepository by lazy { ThreatRepository(this) }
    private val threatFeedUpdater by lazy { ThreatFeedUpdater(this, updateExecutor) }

    private lateinit var protectionStatus: LinearLayout
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var activateButton: Button
    private lateinit var notificationButton: Button
    private lateinit var urlInput: EditText
    private lateinit var brandSpinner: Spinner
    private lateinit var resultPanel: LinearLayout
    private lateinit var resultTitle: TextView
    private lateinit var resultBody: TextView
    private lateinit var resultOfficialButton: Button
    private lateinit var shareResultButton: Button
    private lateinit var updateStatus: TextView
    private lateinit var checkUpdateButton: Button
    private lateinit var threatDataStatus: TextView
    private lateinit var refreshThreatDataButton: Button
    private var lastAssessment: Assessment? = null
    private var lastAssessedUrl: String? = null
    private var pendingInstallFile: File? = null
    private var automaticCheckStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        configureSystemInsets()
        bindViews()
        configureBrandSpinner()
        configureActions()
        handleIncomingIntent(intent)
        refreshThreatData(force = false)
    }

    private fun configureSystemInsets() {
        val scrollView = findViewById<View>(R.id.mainScroll)
        scrollView.setOnApplyWindowInsetsListener { view, insets ->
            val top: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
                top = systemBars.top
                bottom = systemBars.bottom
            } else {
                @Suppress("DEPRECATION")
                top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottom = insets.systemWindowInsetBottom
            }
            view.setPadding(0, top, 0, bottom)
            insets
        }
        scrollView.requestApplyInsets()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updateProtectionStatus()
        updateNotificationPermission()
        showThreatStats(threatRepository.stats())
        resumePendingInstallation()
        maybeCheckForUpdatesAutomatically()
    }

    override fun onDestroy() {
        updateExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) updateNotificationPermission()
    }

    private fun bindViews() {
        protectionStatus = findViewById(R.id.protectionStatus)
        statusTitle = findViewById(R.id.statusTitle)
        statusDetail = findViewById(R.id.statusDetail)
        activateButton = findViewById(R.id.activateButton)
        notificationButton = findViewById(R.id.notificationButton)
        urlInput = findViewById(R.id.urlInput)
        brandSpinner = findViewById(R.id.brandSpinner)
        resultPanel = findViewById(R.id.resultPanel)
        resultTitle = findViewById(R.id.resultTitle)
        resultBody = findViewById(R.id.resultBody)
        resultOfficialButton = findViewById(R.id.resultOfficialButton)
        shareResultButton = findViewById(R.id.shareResultButton)
        updateStatus = findViewById(R.id.updateStatus)
        checkUpdateButton = findViewById(R.id.checkUpdateButton)
        threatDataStatus = findViewById(R.id.threatDataStatus)
        refreshThreatDataButton = findViewById(R.id.refreshThreatDataButton)
        updateStatus.text = getString(R.string.update_status_initial, currentVersionName())
    }

    private fun configureBrandSpinner() {
        val labels = listOf(getString(R.string.automatic_brand)) + BrandCatalog.brands.map { it.displayName }
        brandSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun configureActions() {
        activateButton.setOnClickListener {
            showAccessibilityDisclosure()
        }
        notificationButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
            }
        }
        findViewById<Button>(R.id.verifyButton).setOnClickListener {
            verifyInput(UrlSource.MANUAL)
        }
        checkUpdateButton.setOnClickListener { checkForUpdates(showCurrentResult = true) }
        refreshThreatDataButton.setOnClickListener { refreshThreatData(force = true) }
        findViewById<Button>(R.id.sendFeedbackButton).setOnClickListener { shareFeedback(includeResult = false) }
        shareResultButton.setOnClickListener { shareFeedback(includeResult = true) }
    }

    private fun showAccessibilityDisclosure() {
        AlertDialog.Builder(this)
            .setTitle(R.string.disclosure_title)
            .setMessage(R.string.disclosure_body)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.disclosure_continue) { _, _ ->
                runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    .onFailure {
                        Toast.makeText(this, R.string.open_accessibility_settings, Toast.LENGTH_LONG).show()
                    }
            }
            .show()
    }

    private fun verifyInput(source: UrlSource) {
        val rawUrl = urlInput.text?.toString().orEmpty()
        val normalized = normalizer.normalize(rawUrl)
        if (normalized == null) {
            urlInput.error = getString(R.string.invalid_url)
            resultPanel.visibility = View.GONE
            return
        }

        urlInput.error = null
        val selectedBrandId = brandSpinner.selectedItemPosition
            .takeIf { it > 0 }
            ?.let { BrandCatalog.brands[it - 1].id }
        val localAssessment = analyzer.analyze(
            rawUrl = rawUrl,
            source = source,
            claimedBrandId = selectedBrandId,
        )
        val reputationAssessment = if (localAssessment.verdict == Verdict.OFFICIAL) {
            null
        } else {
            threatRepository.assessHost(normalized.asciiHost)
        }
        val assessment = if (reputationAssessment != null && reputationAssessment.brand == null && localAssessment.brand != null) {
            reputationAssessment.copy(brand = localAssessment.brand, verdict = Verdict.IMPERSONATION)
        } else {
            reputationAssessment ?: localAssessment
        }
        lastAssessment = assessment
        lastAssessedUrl = rawUrl
        showAssessment(assessment)
        currentFocus?.let { focusedView ->
            getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(focusedView.windowToken, 0)
        }
    }

    private fun showAssessment(assessment: Assessment) {
        resultPanel.visibility = View.VISIBLE
        resultOfficialButton.visibility = View.GONE
        resultOfficialButton.setOnClickListener(null)
        shareResultButton.visibility = View.VISIBLE

        val host = assessment.observedAsciiHost.orEmpty()
        val brand = assessment.brand
        when (assessment.verdict) {
            Verdict.OFFICIAL -> {
                resultPanel.setBackgroundResource(R.drawable.bg_result_safe)
                resultTitle.setText(R.string.result_official_title)
                resultTitle.setTextColor(getColor(R.color.success))
                resultBody.text = getString(R.string.result_official_body, host, brand?.displayName.orEmpty())
            }

            Verdict.IMPERSONATION -> {
                resultPanel.setBackgroundResource(R.drawable.bg_result_danger)
                resultTitle.setText(R.string.result_impersonation_title)
                resultTitle.setTextColor(getColor(R.color.danger))
                resultBody.text = getString(
                    R.string.result_impersonation_body,
                    host,
                    brand?.displayName.orEmpty(),
                    brand?.officialUrl.orEmpty(),
                )
                showOfficialButton(assessment)
            }

            Verdict.KNOWN_THREAT -> {
                resultPanel.setBackgroundResource(R.drawable.bg_result_danger)
                resultTitle.setText(R.string.result_known_threat_title)
                resultTitle.setTextColor(getColor(R.color.danger))
                resultBody.text = getString(R.string.result_known_threat_body, host)
            }

            Verdict.SUSPICIOUS -> {
                resultPanel.setBackgroundResource(R.drawable.bg_result_danger)
                resultTitle.setText(R.string.result_suspicious_title)
                resultTitle.setTextColor(getColor(R.color.danger))
                resultBody.text = getString(
                    R.string.result_suspicious_body,
                    host,
                    brand?.displayName.orEmpty(),
                )
                showOfficialButton(assessment)
            }

            Verdict.NO_EVIDENCE -> {
                resultPanel.setBackgroundResource(R.drawable.bg_result_neutral)
                resultTitle.setText(R.string.result_no_evidence_title)
                resultTitle.setTextColor(getColor(R.color.ink))
                resultBody.setText(R.string.result_no_evidence_body)
            }

            Verdict.UNVERIFIABLE,
            Verdict.UNSUPPORTED_SCHEME,
            -> {
                resultPanel.setBackgroundResource(R.drawable.bg_result_neutral)
                resultTitle.setText(R.string.invalid_url)
                resultTitle.setTextColor(getColor(R.color.danger))
                resultBody.text = ""
            }
        }
    }

    private fun showOfficialButton(assessment: Assessment) {
        val url = assessment.officialUrl ?: return
        resultOfficialButton.visibility = View.VISIBLE
        resultOfficialButton.setOnClickListener { openUrl(url) }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, url, Toast.LENGTH_LONG).show() }
    }

    private fun handleIncomingIntent(incomingIntent: Intent?) {
        if (incomingIntent?.action != Intent.ACTION_SEND || incomingIntent.type != "text/plain") return
        val sharedText = incomingIntent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val extractedUrl = normalizer.extractFromSharedText(sharedText) ?: return
        urlInput.setText(extractedUrl)
        brandSpinner.setSelection(0)
        Toast.makeText(this, R.string.shared_url_ready, Toast.LENGTH_SHORT).show()
        verifyInput(UrlSource.SHARE_INTENT)
    }

    private fun updateProtectionStatus() {
        val enabled = isProtectionEnabled()
        if (enabled) {
            protectionStatus.setBackgroundResource(R.drawable.bg_status_active)
            statusTitle.setText(R.string.status_active)
            statusTitle.setTextColor(getColor(R.color.success))
            statusDetail.setText(R.string.status_active_detail)
            activateButton.setText(R.string.open_accessibility_settings)
        } else {
            protectionStatus.setBackgroundResource(R.drawable.bg_status_inactive)
            statusTitle.setText(R.string.status_inactive)
            statusTitle.setTextColor(getColor(R.color.danger))
            statusDetail.setText(R.string.status_inactive_detail)
            activateButton.setText(R.string.activate_protection)
        }
        protectionStatus.contentDescription = "${statusTitle.text}. ${statusDetail.text}"
    }

    private fun isProtectionEnabled(): Boolean {
        val target = ComponentName(this, BrowserProtectionService::class.java)
        val manager = getSystemService(AccessibilityManager::class.java)
        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val serviceInfo = info.resolveInfo.serviceInfo
                ComponentName(serviceInfo.packageName, serviceInfo.name) == target
            }
    }

    private fun updateNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            notificationButton.visibility = View.GONE
            return
        }

        val allowed = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        notificationButton.visibility = if (allowed) View.GONE else View.VISIBLE
        if (allowed) notificationButton.setText(R.string.notification_allowed)
    }

    private fun maybeCheckForUpdatesAutomatically() {
        if (automaticCheckStarted) return
        val lastCheck = updatePreferences.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        if (System.currentTimeMillis() - lastCheck < UpdateConfig.CHECK_INTERVAL_MS) return
        automaticCheckStarted = true
        checkForUpdates(showCurrentResult = false)
    }

    private fun checkForUpdates(showCurrentResult: Boolean) {
        setUpdateBusy(true)
        updateStatus.setText(R.string.update_status_checking)
        val client = GitHubUpdateClient(currentVersionCode())
        updateExecutor.execute {
            val result = runCatching { client.check() }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                setUpdateBusy(false)
                result.onSuccess { checkResult ->
                    updatePreferences.edit()
                        .putLong(KEY_LAST_UPDATE_CHECK, System.currentTimeMillis())
                        .apply()
                    when (checkResult) {
                        is UpdateCheckResult.Available -> {
                            updateStatus.text = getString(
                                R.string.update_status_available,
                                checkResult.manifest.versionName,
                            )
                            showUpdateDialog(checkResult)
                        }
                        is UpdateCheckResult.UpToDate -> {
                            updateStatus.text = getString(
                                R.string.update_status_current,
                                currentVersionName(),
                            )
                            if (showCurrentResult) {
                                Toast.makeText(this, updateStatus.text, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }.onFailure(::showUpdateFailure)
            }
        }
    }

    private fun showUpdateDialog(update: UpdateCheckResult.Available) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle(R.string.update_dialog_title)
            .setMessage(getString(R.string.update_dialog_body, update.manifest.versionName))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.install_update) { _, _ -> downloadUpdate(update) }
            .show()
    }

    private fun downloadUpdate(update: UpdateCheckResult.Available) {
        setUpdateBusy(true)
        updateStatus.text = getString(R.string.update_status_downloading, update.manifest.versionName)
        val destination = File(cacheDir, "updates/SupSecu-${update.manifest.versionCode}.apk")
        val client = GitHubUpdateClient(currentVersionCode())
        updateExecutor.execute {
            val result = runCatching {
                client.download(update, destination).also { apk ->
                    if (!updateInstaller.verify(apk)) {
                        apk.delete()
                        error("La signature de l’APK ne correspond pas à SupSécu.")
                    }
                }
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                setUpdateBusy(false)
                result.onSuccess(::requestUpdateInstallation).onFailure(::showUpdateFailure)
            }
        }
    }

    private fun requestUpdateInstallation(apk: File) {
        pendingInstallFile = apk
        updatePreferences.edit().putString(KEY_PENDING_APK_NAME, apk.name).apply()
        if (!updateInstaller.canInstallPackages()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.update_title)
                .setMessage(R.string.unknown_sources_explanation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.disclosure_continue) { _, _ ->
                    startActivity(updateInstaller.unknownSourcesSettingsIntent())
                }
                .show()
            return
        }
        installPendingUpdate()
    }

    private fun resumePendingInstallation() {
        if (pendingInstallFile == null) {
            val savedName = updatePreferences.getString(KEY_PENDING_APK_NAME, null)
                ?.takeIf { it.matches(Regex("SupSecu-[0-9]+\\.apk")) }
            pendingInstallFile = savedName?.let { File(cacheDir, "updates/$it") }?.takeIf(File::isFile)
            if (savedName != null && pendingInstallFile == null) {
                updatePreferences.edit().remove(KEY_PENDING_APK_NAME).apply()
            }
        }
        if (pendingInstallFile != null && updateInstaller.canInstallPackages()) installPendingUpdate()
    }

    private fun installPendingUpdate() {
        val apk = pendingInstallFile?.takeIf(File::isFile) ?: return
        updateStatus.setText(R.string.update_status_ready)
        runCatching { updateInstaller.install(apk) }
            .onSuccess {
                pendingInstallFile = null
                updatePreferences.edit().remove(KEY_PENDING_APK_NAME).apply()
            }
            .onFailure(::showUpdateFailure)
    }

    private fun showUpdateFailure(failure: Throwable) {
        val message = failure.message?.take(240) ?: failure.javaClass.simpleName
        updateStatus.text = getString(R.string.update_status_error, message)
    }

    private fun setUpdateBusy(busy: Boolean) {
        checkUpdateButton.isEnabled = !busy
    }

    private fun refreshThreatData(force: Boolean) {
        refreshThreatDataButton.isEnabled = false
        threatDataStatus.setText(R.string.threat_data_refreshing)
        threatFeedUpdater.refresh(force) { result ->
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                refreshThreatDataButton.isEnabled = true
                when (result) {
                    is ThreatRefreshResult.Updated -> showThreatStats(result.stats)
                    is ThreatRefreshResult.Current -> showThreatStats(result.stats)
                    is ThreatRefreshResult.Failed -> {
                        showThreatStats(result.previousStats)
                        Toast.makeText(
                            this,
                            getString(R.string.threat_data_error, result.error.message?.take(120).orEmpty()),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    private fun showThreatStats(stats: ThreatFeedStats) {
        threatDataStatus.text = if (stats.domainCount <= 0 || stats.updatedAtMillis <= 0L) {
            getString(R.string.threat_data_empty)
        } else {
            val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(stats.updatedAtMillis))
            getString(R.string.threat_data_ready, stats.domainCount, date)
        }
    }

    private fun shareFeedback(includeResult: Boolean) {
        val subject = getString(R.string.feedback_subject)
        val diagnostics = buildString {
            appendLine(getString(R.string.feedback_template_intro))
            appendLine()
            if (includeResult) {
                appendLine("URL : ${lastAssessedUrl.orEmpty()}")
                appendLine("Résultat : ${lastAssessment?.verdict ?: "inconnu"}")
                appendLine("Marque : ${lastAssessment?.brand?.displayName ?: "non reconnue"}")
                appendLine()
            }
            appendLine("SupSécu ${currentVersionName()}")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Appareil : ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine(getString(R.string.feedback_privacy_note))
        }
        val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", FeedbackConfig.EMAIL, null)).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, diagnostics)
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(FeedbackConfig.EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, diagnostics)
        }
        runCatching { startActivity(Intent.createChooser(emailIntent, getString(R.string.send_feedback))) }
            .recoverCatching { startActivity(Intent.createChooser(shareIntent, getString(R.string.send_feedback))) }
            .onFailure { Toast.makeText(this, R.string.no_share_app, Toast.LENGTH_LONG).show() }
    }

    @Suppress("DEPRECATION")
    private fun currentVersionCode(): Long {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }
    }

    private fun currentVersionName(): String =
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST = 10
        private const val KEY_LAST_UPDATE_CHECK = "last_successful_check"
        private const val KEY_PENDING_APK_NAME = "pending_apk_name"
    }
}
