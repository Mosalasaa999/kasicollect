package za.org.kasicollect.reclaimer.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import za.org.kasicollect.reclaimer.data.AppDatabase
import za.org.kasicollect.reclaimer.data.SyncState
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

class QueueSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val database: AppDatabase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val pendingRecords = database.transactionDao().getPendingTransactions()
        if (pendingRecords.isEmpty()) return Result.success()

        val jsonArray = JSONArray()
        for (record in pendingRecords) {
            val item = JSONObject().apply {
                put("transaction_id", record.transactionId)
                put("manifest_id", record.manifestId)
                put("reclaimer_hash", record.reclaimerHash)
                put("material", record.materialClass)
                put("net_kg", record.netWeightKg)
                put("tare_kg", record.tareWeightKg)
                put("scale_mac", record.scaleMac)
                put("timestamp", record.deviceTimestampUtc)
                put("lat_masked", record.maskedLatitude)
                put("lng_masked", record.maskedLongitude)
                put("hmac", record.payloadHmac)
            }
            jsonArray.put(item)
        }

        val postData = JSONObject().apply {
            put("batch_size", pendingRecords.size)
            put("records", jsonArray)
        }.toString()

        return try {
            val conn = (URL("https://gateway.kasicollect.org.za/v1/sync/batch").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
            }

            OutputStreamWriter(conn.outputStream).use { it.write(postData) }

            if (conn.responseCode == 200) {
                pendingRecords.forEach { record ->
                    database.transactionDao().updateSyncStatus(record.transactionId, SyncState.SYNCED)
                }
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
