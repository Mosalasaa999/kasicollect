package za.org.kasicollect.reclaimer.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class QueueSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return Result.success()
    }
}
