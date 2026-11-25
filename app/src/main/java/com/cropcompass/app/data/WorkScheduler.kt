package com.cropcompass.app.data

import android.content.Context
import androidx.work.*
import com.cropcompass.app.CropCompassApplication
import com.google.firebase.firestore.FieldValue
import com.google.firebase.Timestamp
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.concurrent.TimeUnit
import androidx.room.withTransaction

object WorkScheduler {
    fun schedule(context: Context) {
        Alerts.channel(context)
        val work=WorkManager.getInstance(context)
        work.enqueueUniquePeriodicWork("watchlist-alerts",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<AlertWorker>(15,TimeUnit.MINUTES).build())
        work.enqueueUniquePeriodicWork("report-sync",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<ReportSyncWorker>(15,TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        if(com.cropcompass.app.BuildConfig.DATA_GOV_API_KEY.isNotBlank() && com.cropcompass.app.BuildConfig.DATA_GOV_RESOURCE_ID.isNotBlank()) work.enqueueUniquePeriodicWork("market-refresh",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<MarketRefreshWorker>(6,TimeUnit.HOURS).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
    fun reports(context: Context) { WorkManager.getInstance(context).enqueueUniqueWork("report-sync-now",ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<ReportSyncWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()) }
}
class AlertWorker(context: Context, params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result = try { Alerts.check((applicationContext as CropCompassApplication).container); Result.success() }
    catch(e:Exception){if(e is CancellationException) throw e;Result.retry()}
}
private val syncMutex=Mutex()
private suspend fun removeCloudPhoto(uid:String,id:String) {
    if(com.google.firebase.FirebaseApp.getInstance().options.storageBucket.isNullOrBlank())return
    try{FirebaseStorage.getInstance().reference.child("report_photos/$uid/$id.jpg").delete().await()}
    catch(e:StorageException){if(e.errorCode !in listOf(StorageException.ERROR_OBJECT_NOT_FOUND,StorageException.ERROR_BUCKET_NOT_FOUND))throw e}
}
class ReportSyncWorker(context: Context, params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result = syncMutex.withLock {
        val container=(applicationContext as CropCompassApplication).container
        val uid=container.accounts.uid ?: return@withLock Result.success()
        val db=container.firestore ?: return@withLock Result.success()
        val dao=container.market.dao
        try {
            for(report in dao.pendingReports(uid)) {
                if(container.accounts.uid!=uid) return@withLock Result.success()
                val photoPath="report_photos/$uid/${report.id}.jpg"
                if(report.deleted) {
                    db.collection("price_reports").document(report.id).delete().await()
                    removeCloudPhoto(uid,report.id)
                    dao.deleteReport(report.id)
                } else {
                    if(report.photoUri!=null) FirebaseStorage.getInstance().reference.child(photoPath).putFile(android.net.Uri.parse(report.photoUri)).await() else removeCloudPhoto(uid,report.id)
                    val fields=mapOf("reportId" to report.id,"uid" to uid,"mandiId" to report.mandiId,"commodityId" to report.commodityId,"reportedPrice" to report.price,"observationDate" to report.observationDate,"notes" to report.note.orEmpty(),"photoPath" to if(report.photoUri!=null) photoPath else null,"status" to "UNVERIFIED","dataSource" to (dao.currentInfo()?.dataSource ?: "fixture"),"createdAt" to Timestamp(Date(report.createdAt)),"updatedAt" to FieldValue.serverTimestamp())
                    db.collection("price_reports").document(report.id).set(fields).await()
                    dao.markSynced(report.id,report.updatedAt)
                }
            }
            // Pending local edits take precedence over server reads.
            val remote=db.collection("price_reports").whereEqualTo("uid",uid).get(com.google.firebase.firestore.Source.SERVER).await()
            for(document in remote.documents) {
                val local=dao.report(document.id)
                if(local!=null && !local.synced) continue
                val updated=document.getTimestamp("updatedAt")?.toDate()?.time ?: continue
                if(local!=null && local.updatedAt>=updated) continue
                val price=document.getDouble("reportedPrice")?.takeIf{it.isFinite() && it>0} ?: continue
                var photo:String?=null
                val path=document.getString("photoPath")
                if(path!=null && path.startsWith("report_photos/$uid/")) {
                    val directory=java.io.File(applicationContext.filesDir,"report_photos").apply{mkdirs()}
                    val file=java.io.File(directory,"${document.id}-$updated.jpg")
                    FirebaseStorage.getInstance().reference.child(path).getFile(file).await()
                    photo=android.net.Uri.fromFile(file).toString()
                }
                val restored=com.cropcompass.app.data.local.FarmerReport(document.id,document.getString("mandiId") ?: continue,document.getString("commodityId") ?: continue,price,document.getString("notes"),photo,document.getTimestamp("createdAt")?.toDate()?.time ?: updated,document.getString("observationDate") ?: continue,true,uid,false,updated)
                container.database.withTransaction { val current=dao.report(document.id);if(current==null || current.synced && current.updatedAt<=updated)dao.report(restored) }
            }
            // Keep cached reports if absent from a remote result. No destructive reconciliation engine.
            Result.success()
        } catch(e:Exception){if(e is CancellationException) throw e;Result.retry()}
    }
}
class MarketRefreshWorker(context: Context,params: WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result=try{val container=(applicationContext as CropCompassApplication).container;if(container.market.dao.currentInfo()?.dataSource=="real")container.remote.refresh();Result.success()}catch(e:Exception){if(e is CancellationException)throw e;Result.retry()}
}
