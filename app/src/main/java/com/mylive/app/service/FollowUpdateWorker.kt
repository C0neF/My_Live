package com.mylive.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mylive.app.MainActivity
import com.mylive.app.R
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.ui.navigation.Route
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class FollowUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val followStatusRefreshCoordinator: FollowStatusRefreshCoordinator
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            followStatusRefreshCoordinator.refreshAll().forEach { transition ->
                if (transition.follow.isSpecialFollow && transition.becameLive) {
                    sendNotification(transition.follow)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "FollowUpdateWorker failed")
            Result.retry()
        }
    }

    private fun sendNotification(follow: FollowUserEntity) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "follow_channel"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "特别关注开播提醒",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val route = "/room/detail/${follow.roomId}?siteId=${follow.siteId}"
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(MainActivity.EXTRA_INITIAL_ROUTE, route)
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            follow.id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val siteName = when (follow.siteId) {
            "bilibili" -> "哔哩哔哩"
            "douyu" -> "斗鱼"
            "huya" -> "虎牙"
            "douyin" -> "抖音"
            else -> follow.siteId
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("特别关注开播啦")
            .setContentText("您关注的「${follow.userName}」已在${siteName}开播！")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        manager.notify(follow.id.hashCode(), notification)
    }
}
