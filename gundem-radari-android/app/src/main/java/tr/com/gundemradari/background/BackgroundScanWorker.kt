package tr.com.gundemradari.background

import android.content.Context
import androidx.work.*
import tr.com.gundemradari.data.AppDatabase
import tr.com.gundemradari.data.SourceRepository
import tr.com.gundemradari.scan.ScanCoordinator
import java.util.concurrent.TimeUnit

class BackgroundScanWorker(
    appContext:Context,
    params:WorkerParameters
):CoroutineWorker(appContext,params){
    override suspend fun doWork():Result{
        return runCatching{
            val db=AppDatabase.get(applicationContext)
            SourceRepository(applicationContext,db.dao()).seed()
            ScanCoordinator(db).scan({},waitForReview=true)
            Result.success()
        }.getOrElse{
            Result.retry()
        }
    }
}

object BackgroundScanScheduler{
    private const val WORK_NAME="gundem-radari-periodic-scan"

    fun ensureScheduled(context:Context){
        val constraints=Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request=PeriodicWorkRequestBuilder<BackgroundScanWorker>(
            15,TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
