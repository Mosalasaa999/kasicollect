package za.org.kasicollect.reclaimer.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class CollectionRecord(
    val transactionId: String,
    val manifestId: String,
    val reclaimerHash: String,
    val materialClass: String,
    val grossWeightKg: Double,
    val tareWeightKg: Double,
    val netWeightKg: Double,
    val scaleMac: String,
    val deviceTimestampUtc: Long,
    val maskedLatitude: Double,
    val maskedLongitude: Double,
    val syncState: String,
    val payloadHmac: String
)

class LocalDbHelper(context: Context) : SQLiteOpenHelper(context, "kasicollect_ledger.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE transaction_ledger (
                transactionId TEXT PRIMARY KEY,
                manifestId TEXT,
                reclaimerHash TEXT,
                materialClass TEXT,
                grossWeightKg REAL,
                tareWeightKg REAL,
                netWeightKg REAL,
                scaleMac TEXT,
                deviceTimestampUtc INTEGER,
                maskedLatitude REAL,
                maskedLongitude REAL,
                syncState TEXT,
                payloadHmac TEXT
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS transaction_ledger")
        onCreate(db)
    }

    fun insertTransaction(record: CollectionRecord) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("transactionId", record.transactionId)
            put("manifestId", record.manifestId)
            put("reclaimerHash", record.reclaimerHash)
            put("materialClass", record.materialClass)
            put("grossWeightKg", record.grossWeightKg)
            put("tareWeightKg", record.tareWeightKg)
            put("netWeightKg", record.netWeightKg)
            put("scaleMac", record.scaleMac)
            put("deviceTimestampUtc", record.deviceTimestampUtc)
            put("maskedLatitude", record.maskedLatitude)
            put("maskedLongitude", record.maskedLongitude)
            put("syncState", record.syncState)
            put("payloadHmac", record.payloadHmac)
        }
        db.insertWithOnConflict("transaction_ledger", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getPendingCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM transaction_ledger WHERE syncState = 'QUEUED'", null)
        var count = 0
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }
        cursor.close()
        return count
    }
}
