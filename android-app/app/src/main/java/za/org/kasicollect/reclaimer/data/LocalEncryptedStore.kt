package za.org.kasicollect.reclaimer.data

import android.content.Context
import androidx.room.*

@Entity(tableName = "transaction_ledger")
data class CollectionRecord(
    @PrimaryKey val transactionId: String,
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

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(record: CollectionRecord)

    @Query("SELECT * FROM transaction_ledger WHERE syncState = 'QUEUED' ORDER BY deviceTimestampUtc ASC")
    suspend fun getPendingTransactions(): List<CollectionRecord>

    @Query("UPDATE transaction_ledger SET syncState = :state WHERE transactionId = :txId")
    suspend fun updateSyncStatus(txId: String, state: String)

    @Query("SELECT COUNT(*) FROM transaction_ledger WHERE syncState = 'QUEUED'")
    suspend fun getPendingCount(): Int
}

@Database(entities = [CollectionRecord::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kasicollect_ledger.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
