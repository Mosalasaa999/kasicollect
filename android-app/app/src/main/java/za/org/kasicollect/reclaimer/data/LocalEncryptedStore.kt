package za.org.kasicollect.reclaimer.data

import androidx.room.*

enum class SyncState { QUEUED, TRANSMITTING, SYNCED, REJECTED }

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
    val syncState: SyncState,
    val payloadHmac: String
)

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(record: CollectionRecord)

    @Query("SELECT * FROM transaction_ledger WHERE sync_state = 'QUEUED' ORDER BY deviceTimestampUtc ASC")
    fun getPendingTransactions(): List<CollectionRecord>

    @Query("UPDATE transaction_ledger SET syncState = :state WHERE transactionId = :txId")
    suspend fun updateSyncStatus(txId: String, state: SyncState)
}

@Database(entities = [CollectionRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
}
