package com.cropcompass.app.data.remote

import com.cropcompass.app.BuildConfig
import com.cropcompass.app.data.local.PriceRecord
import com.cropcompass.app.data.repository.MarketSnapshot
import com.cropcompass.app.data.repository.MarketRepository
import androidx.room.withTransaction
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

data class DataGovResponse(val records: List<Map<String,String?>>?, val total: Int?)
interface DataGovApi {
    @GET("resource/{resource}") suspend fun prices(@Path("resource") resource: String,@Query("api-key") key: String,@Query("filters[state]") state: String = "Karnataka",@Query("format") format: String = "json",@Query("limit") limit: Int = 1000,@Query("offset") offset: Int = 0): DataGovResponse
}
object DataGovMapper {
    private fun normalize(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+")," ")
    fun map(record: Map<String,String?>, data: MarketSnapshot, today: LocalDate = LocalDate.now()): PriceRecord? {
        fun field(vararg keys: String): String? = keys.firstNotNullOfOrNull { key->record.entries.find{it.key.equals(key,true)}?.value?.trim()?.takeIf{it.isNotBlank()} }
        if(!field("state","state_name").equals("Karnataka",true)) return null
        val district=(field("district","district_name") ?: return null).let{if(it.equals("Ramanagara",true)) "Bengaluru South" else it}
        val candidates=data.mandis.filter { m->data.districts.find{it.id==m.districtId}?.nameEn?.let{normalize(it)==normalize(district)}==true }
        val code=field("market_code");val name=field("market","market_center")
        val mandi=candidates.singleOrNull{if(code!=null)it.id==code else name!=null && normalize(it.nameEn)==normalize(name)} ?: return null
        val crop=data.commodities.singleOrNull{field("commodity")?.let{value->normalize(value)==normalize(it.nameEn)}==true} ?: return null
        val rawDate=field("arrival_date","report_date") ?: return null
        val date=runCatching{LocalDate.parse(rawDate)}.getOrElse{runCatching{LocalDate.parse(rawDate,DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT))}.getOrNull() ?: return null}
        if(date>today || date<today.minusDays(30)) return null
        val unit=field("price_unit")?.lowercase()?.replace(Regex("[^a-z]"),"")
        if(unit!=null && unit !in listOf("rsquintal","inrquintal","quintal","rsqtl")) return null
        fun number(key: String)=field(key)?.replace(",","")?.toDoubleOrNull()?.takeIf{it.isFinite() && it>0}
        val modal=number("modal_price") ?: return null
        val rawVariety=field("variety")
        val variety=data.prices.firstOrNull{it.commodityId==crop.id && it.variety.equals(rawVariety,true)}?.variety ?: rawVariety
        val id=MessageDigest.getInstance("SHA-256").digest("${mandi.id}|${crop.id}|${variety ?: "None"}|$date".toByteArray()).joinToString(""){"%02x".format(it)}.take(24)
        return PriceRecord(id,mandi.id,crop.id,variety,date.toString(),number("min_price")?.takeIf{it<=modal},number("max_price")?.takeIf{it>=modal},modal,field("arrivals_tonnes")?.toDoubleOrNull()?.takeIf{it.isFinite() && it>=0},"real")
    }
}
class RemoteRefresh(private val repository: MarketRepository) {
    val configured get()=BuildConfig.DATA_GOV_API_KEY.isNotBlank() && BuildConfig.DATA_GOV_RESOURCE_ID.isNotBlank()
    private val api by lazy { Retrofit.Builder().baseUrl("https://api.data.gov.in/").client(OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).build()).addConverterFactory(GsonConverterFactory.create()).build().create(DataGovApi::class.java) }
    suspend fun refresh(): String {
        if(!configured) return "Data as of ${repository.dao.currentInfo()?.lastDate ?: "—"}"
        val data=repository.snapshot.first();val info=requireNotNull(data.info)
        require(info.dataSource=="real"){"Import the real CSV before enabling official refresh. Test and real records are never mixed."}
        val rows=mutableListOf<PriceRecord>();var offset=0;var skipped=0
        repeat(10) {
            val response=api.prices(BuildConfig.DATA_GOV_RESOURCE_ID,BuildConfig.DATA_GOV_API_KEY,offset=offset)
            val raw=response.records.orEmpty()
            raw.forEach { record->val price=DataGovMapper.map(record,data);if(price!=null) rows.add(price) else skipped++ }
            offset+=raw.size
            if(raw.isEmpty() || raw.size<1000 || offset>=(response.total ?: Int.MAX_VALUE)) {
                if(rows.isNotEmpty()) repository.db.withTransaction { repository.dao.prices(rows); repository.dao.info(info.copy(lastDate=maxOf(info.lastDate,rows.maxOf{it.date}),firstDate=minOf(info.firstDate,rows.minOf{it.date}),recordCount=repository.dao.countPrices(),generatedAt=LocalDate.now().toString())) }
                return "Updated ${rows.size} observations; $skipped unmatched or invalid rows skipped. Cached data remains available offline."
            }
        }
        if(rows.isNotEmpty()) repository.db.withTransaction { repository.dao.prices(rows);repository.dao.info(info.copy(lastDate=maxOf(info.lastDate,rows.maxOf{it.date}),recordCount=repository.dao.countPrices())) }
        return "Updated ${rows.size} observations (10-page limit); narrow the resource if more records are needed."
    }
}
