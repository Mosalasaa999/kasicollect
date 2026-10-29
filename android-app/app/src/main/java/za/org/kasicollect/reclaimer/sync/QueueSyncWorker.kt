package za.org.kasicollect.reclaimer.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import za.org.kasicollect.reclaimer.data.AppDatabase

class QueueSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val pendingRecords = database.transactionDao().getPendingTransactions()

        if (pendingRecords.isEmpty()) {
            return Result.success()
        }

        // Transactions will be drained via reverse-APN gateway
        return Result.success()
    }
}
