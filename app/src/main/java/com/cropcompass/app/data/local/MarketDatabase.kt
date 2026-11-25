package com.cropcompass.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity data class District(@PrimaryKey val id: String, val nameEn: String, val nameKn: String? = null) {
    fun name(kn: Boolean) = if (kn) nameKn ?: nameEn else nameEn
}
@Entity data class Mandi(@PrimaryKey val id: String, val nameEn: String, val nameKn: String? = null, val districtId: String, val lat: Double?, val lng: Double?, val geoConfidence: String, val lastReportDate: String? = null) {
    fun name(kn: Boolean) = if (kn) nameKn ?: nameEn else nameEn
}
@Entity data class Commodity(@PrimaryKey val id: String, val nameEn: String, val nameKn: String? = null, val group: String? = null) {
    fun name(kn: Boolean) = if (kn) nameKn ?: nameEn else nameEn
}
@Entity(indices = [Index(value = ["mandiId", "commodityId", "date"])])
data class PriceRecord(@PrimaryKey val id: String, val mandiId: String, val commodityId: String, val variety: String?, val date: String, val minPrice: Double?, val maxPrice: Double?, val modalPrice: Double, val arrivalsTonnes: Double?, val source: String = "fixture", val unit: String = "INR_QUINTAL")
@Entity data class WatchlistItem(@PrimaryKey val id: String, val commodityId: String, val mandiId: String?, val targetPrice: Double, val direction: String, val enabled: Boolean = true, val createdAt: Long = System.currentTimeMillis(), val lastTriggeredAt: Long? = null, val lastCheckedAt: Long? = null, val lastConditionMet: Boolean = false, val ownerUid: String = "guest")
@Entity data class FarmerReport(@PrimaryKey val id: String, val mandiId: String, val commodityId: String, val price: Double, val note: String? = null, val photoUri: String? = null, val createdAt: Long = System.currentTimeMillis(), val observationDate: String, val synced: Boolean = false, val ownerUid: String = "guest", val deleted: Boolean = false, val updatedAt: Long = System.currentTimeMillis())
@Entity data class DataInfo(@PrimaryKey val id: Int = 1, val dataVersion: String, val dataSource: String, val source: String, val firstDate: String, val lastDate: String, val recordCount: Int, val historyDays: Int, val generatedAt: String, val unit: String)

@Dao interface MarketDao {
    @Query("SELECT * FROM District ORDER BY nameEn") fun districts(): Flow<List<District>>
    @Query("SELECT * FROM Mandi ORDER BY nameEn") fun mandis(): Flow<List<Mandi>>
    @Query("SELECT * FROM Commodity ORDER BY nameEn") fun commodities(): Flow<List<Commodity>>
    @Query("SELECT * FROM PriceRecord ORDER BY date DESC") fun prices(): Flow<List<PriceRecord>>
    @Query("SELECT * FROM DataInfo WHERE id=1") fun info(): Flow<DataInfo?>
    @Query("SELECT * FROM DataInfo WHERE id=1") suspend fun currentInfo(): DataInfo?
    @Query("SELECT * FROM WatchlistItem ORDER BY createdAt DESC") fun watchlist(): Flow<List<WatchlistItem>>
    @Query("SELECT * FROM FarmerReport ORDER BY createdAt DESC") fun reports(): Flow<List<FarmerReport>>
    @Query("SELECT * FROM WatchlistItem WHERE enabled=1") suspend fun activeWatches(): List<WatchlistItem>
    @Query("SELECT * FROM PriceRecord WHERE commodityId=:commodity AND (:mandi IS NULL OR mandiId=:mandi) ORDER BY date DESC, modalPrice DESC LIMIT 1") suspend fun latest(commodity: String, mandi: String?): PriceRecord?
    @Query("SELECT * FROM FarmerReport WHERE synced=0 AND ownerUid=:uid") suspend fun pendingReports(uid: String): List<FarmerReport>
    @Query("SELECT * FROM FarmerReport WHERE id=:id") suspend fun report(id: String): FarmerReport?
    @Query("SELECT * FROM FarmerReport WHERE ownerUid=:uid") suspend fun ownedReports(uid: String): List<FarmerReport>
    @Upsert suspend fun districts(rows: List<District>)
    @Upsert suspend fun mandis(rows: List<Mandi>)
    @Upsert suspend fun commodities(rows: List<Commodity>)
    @Upsert suspend fun prices(rows: List<PriceRecord>)
    @Upsert suspend fun info(row: DataInfo)
    @Upsert suspend fun watch(row: WatchlistItem)
    @Upsert suspend fun report(row: FarmerReport)
    @Query("DELETE FROM WatchlistItem WHERE id=:id") suspend fun deleteWatch(id: String)
    @Query("UPDATE WatchlistItem SET lastCheckedAt=:checked, lastTriggeredAt=:triggered, lastConditionMet=:met WHERE id=:id AND targetPrice=:target AND direction=:direction AND enabled=1") suspend fun markWatchCheck(id: String,checked: Long,triggered: Long?,met: Boolean,target: Double,direction: String)
    @Query("DELETE FROM FarmerReport WHERE id=:id") suspend fun deleteReport(id: String)
    @Query("UPDATE FarmerReport SET synced=1 WHERE id=:id AND updatedAt=:revision") suspend fun markSynced(id: String, revision: Long)
    @Query("SELECT COUNT(*) FROM PriceRecord") suspend fun countPrices(): Int
    @Query("DELETE FROM PriceRecord") suspend fun clearPrices()
    @Query("DELETE FROM Mandi") suspend fun clearMandis()
    @Query("DELETE FROM Commodity") suspend fun clearCommodities()
    @Query("DELETE FROM District") suspend fun clearDistricts()
}
@Database(entities = [District::class, Mandi::class, Commodity::class, PriceRecord::class, WatchlistItem::class, FarmerReport::class, DataInfo::class], version = 1, exportSchema = true)
abstract class MarketDatabase : RoomDatabase() { abstract fun dao(): MarketDao }
