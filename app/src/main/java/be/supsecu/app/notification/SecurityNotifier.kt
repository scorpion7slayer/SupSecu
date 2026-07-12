package be.supsecu.app.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import be.supsecu.app.R
import be.supsecu.app.core.Assessment
import be.supsecu.app.ui.MainActivity

class SecurityNotifier(
    private val context: Context,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    fun showImpersonation(assessment: Assessment) {
        val brand = assessment.brand ?: return
        val officialDomain = Uri.parse(brand.officialUrl).host ?: return
        notify(
            id = IMPERSONATION_NOTIFICATION_ID,
            title = context.getString(R.string.fraud_notification_title),
            body = context.getString(R.string.fraud_notification_text, brand.displayName, officialDomain),
            officialBrandId = brand.id,
        )
    }

    fun showSuspicious(assessment: Assessment) {
        val brand = assessment.brand ?: return
        val officialDomain = Uri.parse(brand.officialUrl).host ?: return
        notify(
            id = SUSPICIOUS_NOTIFICATION_ID,
            title = context.getString(R.string.fraud_notification_title),
            body = context.getString(R.string.fraud_notification_text, brand.displayName, officialDomain),
            officialBrandId = brand.id,
        )
    }

    fun showKnownThreat(assessment: Assessment) {
        if (assessment.observedAsciiHost == null) return
        notify(
            id = KNOWN_THREAT_NOTIFICATION_ID,
            title = context.getString(R.string.known_threat_notification_title),
            body = context.getString(R.string.known_threat_notification_text),
            officialBrandId = null,
        )
    }

    fun cancelAlerts() {
        manager.cancel(IMPERSONATION_NOTIFICATION_ID)
        manager.cancel(SUSPICIOUS_NOTIFICATION_ID)
        manager.cancel(KNOWN_THREAT_NOTIFICATION_ID)
    }

    private fun notify(id: Int, title: String, body: String, officialBrandId: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        createChannel()
        val contentIntent = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val leaveIntent = PendingIntent.getBroadcast(
            context,
            id + LEAVE_REQUEST_OFFSET,
            Intent(context, SecurityNotificationActionReceiver::class.java).apply {
                action = SecurityNotificationActionReceiver.ACTION_LEAVE_PAGE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(Color.rgb(172, 27, 24))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ERROR)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.leave_page), leaveIntent).build())
        if (officialBrandId != null) {
            val officialIntent = PendingIntent.getBroadcast(
                context,
                id + OFFICIAL_REQUEST_OFFSET,
                Intent(context, SecurityNotificationActionReceiver::class.java).apply {
                    action = SecurityNotificationActionReceiver.ACTION_OPEN_OFFICIAL
                    putExtra(SecurityNotificationActionReceiver.EXTRA_BRAND_ID, officialBrandId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(R.string.open_official_site),
                    officialIntent,
                ).build(),
            )
        }

        manager.notify(id, builder.build())
    }

    companion object {
        private const val CHANNEL_ID = "security_alerts"
        private const val IMPERSONATION_NOTIFICATION_ID = 2_001
        private const val SUSPICIOUS_NOTIFICATION_ID = 2_002
        private const val KNOWN_THREAT_NOTIFICATION_ID = 2_003
        private const val LEAVE_REQUEST_OFFSET = 10_000
        private const val OFFICIAL_REQUEST_OFFSET = 20_000
    }
}
