package com.cropcompass.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.cropcompass.app.data.local.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.PushbackReader
import java.io.Reader

data class MarketSnapshot(val districts: List<District> = emptyList(), val mandis: List<Mandi> = emptyList(), val commodities: List<Commodity> = emptyList(), val prices: List<PriceRecord> = emptyList(), val info: DataInfo? = null)

object Csv {
    /** Streaming RFC4180 reader, including quoted commas, doubled quotes, and newlines. */
    fun rows(reader: Reader): Sequence<List<String>> = sequence {
        val input = PushbackReader(reader, 1)
        val row = mutableListOf<String>(); val field = StringBuilder(); var quoted = false
        while (true) {
            val next = input.read()
            if (next == -1) { require(!quoted) { "Unclosed CSV quote" }; if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); yield(row.toList()) }; break }
            val c = next.toChar()
            when {
                c == '"' -> if (quoted) { val peek = input.read(); if (peek == '"'.code) field.append('"') else { quoted = false; if (peek != -1) input.unread(peek) } } else { quoted = true }
                c == ',' && !quoted -> { row.add(field.toString()); field.clear() }
                c == '\n' && !quoted -> { row.add(field.toString().removeSuffix("\r")); yield(row.toList()); row.clear(); field.clear() }
                else -> field.append(c)
            }
        }
    }
    fun price(row: Map<String, String>, source: String): PriceRecord {
        val modal = requireNotNull(row["modalPrice"]?.toDoubleOrNull())
        require(modal.isFinite() && modal > 0)
        java.time.LocalDate.parse(row.getValue("date"))
        return PriceRecord(row.getValue("id"), row.getValue("mandiId"), row.getValue("commodityId"), row["variety"]?.ifBlank { null }, row.getValue("date"), row["minPrice"]?.toDoubleOrNull(), row["maxPrice"]?.toDoubleOrNull(), modal, row["arrivalsTonnes"]?.toDoubleOrNull(), source)
    }
}
class MarketRepository(private val context: Context, val db: MarketDatabase) {
    val dao = db.dao()
    val snapshot = combine(dao.districts(), dao.mandis(), dao.commodities(), dao.prices(), dao.info()) { ds, ms, cs, ps, info -> MarketSnapshot(ds, ms, cs, ps, info) }
    suspend fun seed() = withContext(Dispatchers.IO) {
        val gson = Gson()
        fun <T> asset(name: String, type: Class<T>): T = context.assets.open("data/$name").bufferedReader().use { gson.fromJson(it, type) }
        val meta = asset("metadata.json", DataInfo::class.java).copy(id = 1)
        if (dao.currentInfo()?.dataVersion == meta.dataVersion) return@withContext
        val digest=java.security.MessageDigest.getInstance("SHA-256")
        digest.update(meta.dataSource.toByteArray())
        listOf("prices.csv","mandis.json","commodities.json","districts.json").forEach { name->
            context.assets.open("data/$name").use { stream->val buffer=ByteArray(8192);while(true){val size=stream.read(buffer);if(size<0)break;digest.update(buffer,0,size)} }
        }
        require(digest.digest().joinToString(""){"%02x".format(it)}.take(20)==meta.dataVersion){"Bundled data integrity check failed. Rebuild the data assets before installing."}
        val ds = asset("districts.json", Array<District>::class.java).toList()
        val ms = asset("mandis.json", Array<Mandi>::class.java).toList()
        val cs = asset("commodities.json", Array<Commodity>::class.java).toList()
        val mandiIds=ms.map{it.id}.toSet();val commodityIds=cs.map{it.id}.toSet()
        require(ds.isNotEmpty() && ms.all { market -> ds.any { it.id == market.districtId } })
        db.withTransaction {
            // Only replace market catalogs and prices. Personal tables are never cleared.
            dao.clearPrices(); dao.clearMandis(); dao.clearCommodities(); dao.clearDistricts()
            dao.districts(ds); dao.mandis(ms); dao.commodities(cs)
            context.assets.open("data/prices.csv").bufferedReader().use { reader ->
                val iterator = Csv.rows(reader).iterator()
                val header = iterator.next(); val batch = mutableListOf<PriceRecord>()
                while (iterator.hasNext()) {
                    val row = iterator.next(); require(row.size == header.size)
                    val price = Csv.price(header.zip(row).toMap(), meta.dataSource)
                    require(price.mandiId in mandiIds && price.commodityId in commodityIds)
                    batch.add(price)
                    if (batch.size == 1000) { dao.prices(batch.toList()); batch.clear() }
                }
                if (batch.isNotEmpty()) dao.prices(batch)
            }
            dao.info(meta)
        }
    }
}
