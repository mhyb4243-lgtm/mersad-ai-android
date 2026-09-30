package com.mersadai.app.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mersadai.app.MainActivity
import com.mersadai.app.R
import com.mersadai.app.data.local.ContentDao
import com.mersadai.app.domain.model.AppSettings
import com.mersadai.app.domain.notifications.DiscoveryNotificationCandidate
import com.mersadai.app.domain.notifications.DiscoveryNotificationPolicy
import com.mersadai.app.domain.notifications.DiscoveryNotificationType

class LocalNotificationDispatcher(
    private val context: Context,
    private val dao: ContentDao,
) {
    suspend fun publish(settings: AppSettings) {
        val pending = dao.getPendingNotificationHistory()
        if (pending.isEmpty()) return

        val now = System.currentTimeMillis()
        val permissionGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val plan = DiscoveryNotificationPolicy.plan(
            candidates = pending.map {
                DiscoveryNotificationCandidate(it.notificationKey, it.itemId, it.notificationType, it.discoveredAt)
            },
            settings = settings,
            notificationPermissionGranted = permissionGranted,
            now = now,
        )

        if (plan.batch == null) {
            if (plan.handledKeys.isNotEmpty()) dao.markNotificationsHandled(plan.handledKeys.toList(), now)
            return
        }

        ensureChannel()
        val batch = plan.batch
        val typeSummary = batch.counts.entries.joinToString(" • ") { (type, count) ->
            context.getString(type.labelResource(), count)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_DISCOVERY_SECTION, "LATEST"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_summary, batch.total, typeSummary))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notification_summary, batch.total, typeSummary)))
            .setNumber(batch.total)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        dao.markNotificationsHandled(plan.handledKeys.toList(), now)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 150, 250)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build(),
            )
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun DiscoveryNotificationType.labelResource(): Int = when (this) {
        DiscoveryNotificationType.AI_TOOLS -> R.string.notification_count_ai_tools
        DiscoveryNotificationType.ANDROID_PROJECTS -> R.string.notification_count_android
        DiscoveryNotificationType.MODELS -> R.string.notification_count_models
        DiscoveryNotificationType.PROMPTS -> R.string.notification_count_prompts
        DiscoveryNotificationType.NEWS -> R.string.notification_count_news
    }

    private companion object {
        const val CHANNEL_ID = "mersad_discoveries_v2"
        const val GROUP_KEY = "com.mersadai.app.DISCOVERIES"
        const val NOTIFICATION_ID = 7401
    }
}