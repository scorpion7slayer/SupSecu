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
        val host = assessment.observedAsciiHost ?: return
        val officialUrl = brand.officialUrl
        val body = context.getString(R.string.fraud_notification_text, host, officialUrl)
        notify(
            id = IMPERSONATION_NOTIFICATION_ID,
            title = context.getString(R.string.fraud_notification_title, brand.displayName),
            body = body,
            officialUrl = officialUrl,
        )
    }

    fun showSuspicious(assessment: Assessment) {
        val brand = assessment.brand ?: return
        val host = assessment.observedAsciiHost ?: return
        val body = context.getString(R.string.suspicious_notification_text, host, brand.displayName)
        notify(
            id = SUSPICIOUS_NOTIFICATION_ID,
            title = context.getString(R.string.suspicious_notification_title),
            body = body,
            officialUrl = brand.officialUrl,
        )
    }

    fun cancelAlerts() {
        manager.cancel(IMPERSONATION_NOTIFICATION_ID)
        manager.cancel(SUSPICIOUS_NOTIFICATION_ID)
    }

    private fun notify(id: Int, title: String, body: String, officialUrl: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        createChannel()
        val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse(officialUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(Color.rgb(172, 27, 24))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ERROR)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.open), pendingIntent).build())
            .build()

        manager.notify(id, notification)
    }

    companion object {
        private const val CHANNEL_ID = "security_alerts"
        private const val IMPERSONATION_NOTIFICATION_ID = 2_001
        private const val SUSPICIOUS_NOTIFICATION_ID = 2_002
    }
}
