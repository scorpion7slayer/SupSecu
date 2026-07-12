package be.supsecu.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import be.supsecu.app.core.BrandCatalog
import be.supsecu.app.service.BrowserProtectionService

class SecurityNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SecurityNotifier(context).cancelAlerts()
        when (intent.action) {
            ACTION_LEAVE_PAGE -> BrowserProtectionService.requestLeaveActivePage()
            ACTION_OPEN_OFFICIAL -> {
                val brand = BrandCatalog.byId(intent.getStringExtra(EXTRA_BRAND_ID)) ?: return
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(brand.officialUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }
        }
    }

    companion object {
        const val ACTION_LEAVE_PAGE = "be.supsecu.app.action.LEAVE_PAGE"
        const val ACTION_OPEN_OFFICIAL = "be.supsecu.app.action.OPEN_OFFICIAL"
        const val EXTRA_BRAND_ID = "brand_id"
    }
}
