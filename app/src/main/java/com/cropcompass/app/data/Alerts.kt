package com.cropcompass.app.data

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cropcompass.app.*
import com.cropcompass.app.domain.MarketLogic
import com.cropcompass.app.ui.components.money
import kotlinx.coroutines.flow.first

object Alerts {
    const val CHANNEL="price-targets"
    fun channel(context: Context) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(CHANNEL,"Commodity price targets",NotificationManager.IMPORTANCE_DEFAULT))
    }
    fun permitted(context: Context): Boolean = (Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled()
    fun condition(price: Double, target: Double, direction: String): Boolean = if(direction=="ABOVE") price>=target else price<=target
    @android.annotation.SuppressLint("MissingPermission")
    suspend fun check(container: AppContainer, simulate: Boolean = false): Int {
        val dao=container.market.dao; val data=container.market.snapshot.first(); val owner=container.accounts.uid ?: "guest"; var posted=0
        for(watch in dao.activeWatches().filter { it.ownerUid==owner }) {
            val quote=dao.latest(watch.commodityId,watch.mandiId)
            val valid=quote!=null && MarketLogic.age(quote.date) in 0..7
            val met=valid && condition(quote!!.modalPrice,watch.targetPrice,watch.direction)
            val trigger=valid && (simulate || met && !watch.lastConditionMet)
            var timestamp=watch.lastTriggeredAt
            if(trigger && permitted(container.context)) {
                val crop=data.commodities.find{it.id==quote!!.commodityId}?.nameEn ?: watch.commodityId
                val market=data.mandis.find{it.id==quote!!.mandiId}?.nameEn ?: quote!!.mandiId
                val intent=Intent(container.context,MainActivity::class.java).putExtra("open_route","watchlist").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                val pending=PendingIntent.getActivity(container.context,watch.id.hashCode(),intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val title=(if(simulate) "TEST ALERT • " else "")+(if(quote!!.source=="fixture") "TEST DATA • " else "")+crop
                val body="${money(quote.modalPrice)} / quintal • $market • ${quote.date}. Target ${watch.direction.lowercase()} ${money(watch.targetPrice)}."
                channel(container.context)
                @Suppress("MissingPermission")
                val notification=NotificationCompat.Builder(container.context,CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(pending).setAutoCancel(true).build()
                NotificationManagerCompat.from(container.context).notify(watch.id.hashCode(),notification)
                timestamp=System.currentTimeMillis();posted++
            }
            dao.markWatchCheck(watch.id,System.currentTimeMillis(),timestamp,if(!met) false else if(trigger && !permitted(container.context)) false else met,watch.targetPrice,watch.direction)
        }
        return posted
    }
}
