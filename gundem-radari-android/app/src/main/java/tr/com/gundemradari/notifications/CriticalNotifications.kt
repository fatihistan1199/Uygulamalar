package tr.com.gundemradari.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import tr.com.gundemradari.MainActivity
import tr.com.gundemradari.R
import tr.com.gundemradari.data.EventEntity
import tr.com.gundemradari.data.GundemDao

object CriticalNotificationPrefs {
    private const val PREFS="gundem-radari-critical-notifications"
    private const val ENABLED="enabled"

    fun isEnabled(context:Context):Boolean =
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getBoolean(ENABLED,false)

    fun setEnabled(context:Context,enabled:Boolean){
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ENABLED,enabled)
            .apply()
    }
}

data class NotificationDecision(
    val shouldNotify:Boolean,
    val isUpdate:Boolean=false
)

fun criticalNotificationDecision(
    event:EventEntity,
    scanStartedAt:Long,
    now:Long,
    previousNotifiedAt:Long?,
    previousImportance:Double?
):NotificationDecision{
    if(event.scope=="religion")return NotificationDecision(false)
    if(event.noise>=35.0)return NotificationDecision(false)
    if(event.importance<92.0)return NotificationDecision(false)

    val published=event.publishedAt
    if(
        published!=null &&
        now-published>12L*60*60*1000
    ){
        return NotificationDecision(false)
    }

    if(previousNotifiedAt==null){
        val genuinelyNew=
            event.firstSeenAt>=scanStartedAt-2L*60*1000 &&
            event.updatedAt>=scanStartedAt-2L*60*1000
        return NotificationDecision(genuinelyNew,false)
    }

    val enoughTimePassed=
        now-previousNotifiedAt>=2L*60*60*1000
    val materiallyMoreImportant=
        previousImportance!=null &&
        event.importance>=previousImportance+8.0

    return NotificationDecision(
        shouldNotify=enoughTimePassed&&materiallyMoreImportant,
        isUpdate=true
    )
}

object CriticalNotificationManager {
    private const val CHANNEL_ID="critical_agenda"
    private const val STATE_PREFS="gundem-radari-notification-state"

    fun ensureChannel(context:Context){
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.O)return

        val manager=context.getSystemService(NotificationManager::class.java)
        val channel=NotificationChannel(
            CHANNEL_ID,
            "Kritik gündem",
            NotificationManager.IMPORTANCE_HIGH
        ).apply{
            description="Yalnız çok yüksek önem düzeyindeki gündem olayları"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun hasPermission(context:Context):Boolean{
        return Build.VERSION.SDK_INT<33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )==PackageManager.PERMISSION_GRANTED
    }

    suspend fun dispatchAfterScan(
        context:Context,
        dao:GundemDao,
        scanStartedAt:Long
    ){
        if(!CriticalNotificationPrefs.isEnabled(context))return
        if(!hasPermission(context))return

        ensureChannel(context)

        val now=System.currentTimeMillis()
        val state=context.getSharedPreferences(
            STATE_PREFS,
            Context.MODE_PRIVATE
        )

        val candidates=dao.criticalEvents(
            after=scanStartedAt-2L*60*1000,
            minImportance=92.0
        )

        var sent=0
        for(event in candidates){
            if(sent>=2)break

            val raw=state.getString("event:${event.id}",null)
            val parts=raw?.split("|")
            val previousAt=parts?.getOrNull(0)?.toLongOrNull()
            val previousImportance=parts?.getOrNull(1)?.toDoubleOrNull()

            val decision=criticalNotificationDecision(
                event=event,
                scanStartedAt=scanStartedAt,
                now=now,
                previousNotifiedAt=previousAt,
                previousImportance=previousImportance
            )

            if(!decision.shouldNotify)continue

            notify(context,event,decision.isUpdate)
            state.edit()
                .putString(
                    "event:${event.id}",
                    "$now|${event.importance}"
                )
                .apply()
            sent++
        }

        cleanupState(state,now)
    }

    private fun notify(
        context:Context,
        event:EventEntity,
        isUpdate:Boolean
    ){
        val intent=Intent(context,MainActivity::class.java).apply{
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending=PendingIntent.getActivity(
            context,
            event.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val notification=NotificationCompat.Builder(
            context,
            CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                if(isUpdate)"Kritik gelişme" else "Kritik gündem"
            )
            .setContentText(event.title.take(150))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        buildString{
                            append(event.title)
                            if(event.summary.isNotBlank()){
                                append("\n\n")
                                append(event.summary.take(320))
                            }
                        }
                    )
            )
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_EVENT)
            .setOnlyAlertOnce(false)
            .build()

        NotificationManagerCompat.from(context)
            .notify(event.id.hashCode(),notification)
    }

    private fun cleanupState(
        prefs:android.content.SharedPreferences,
        now:Long
    ){
        val cutoff=now-14L*24*60*60*1000
        val editor=prefs.edit()
        prefs.all.forEach{(key,value)->
            val time=(value as? String)
                ?.substringBefore("|")
                ?.toLongOrNull()
            if(time!=null&&time<cutoff){
                editor.remove(key)
            }
        }
        editor.apply()
    }
}
