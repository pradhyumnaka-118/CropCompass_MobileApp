package com.cropcompass.app.domain

import com.cropcompass.app.data.local.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.*

object MarketLogic {
    fun search(items: List<Commodity>, query: String) = items.filter { it.nameEn.contains(query.trim(), true) || it.nameKn?.contains(query.trim(), true) == true }
    fun age(date: String, today: LocalDate = LocalDate.now()): Long = ChronoUnit.DAYS.between(LocalDate.parse(date), today)
    fun stale(date: String, today: LocalDate = LocalDate.now()) = age(date, today) > 7
    fun latest(rows: List<PriceRecord>) = rows.groupBy { Triple(it.mandiId, it.commodityId, it.variety) }.values.mapNotNull { group -> group.maxByOrNull { it.date } }
    fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = Math.PI / 180
        val a = sin((lat2-lat1)*r/2).pow(2) + cos(lat1*r)*cos(lat2*r)*sin((lng2-lng1)*r/2).pow(2)
        return 6371.0088 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
    fun change(rows: List<PriceRecord>, record: PriceRecord): Double? {
        val previous = rows.filter { it.mandiId == record.mandiId && it.commodityId == record.commodityId && it.variety == record.variety && it.source == record.source && it.date < record.date }.maxByOrNull { it.date } ?: return null
        return (record.modalPrice-previous.modalPrice)/previous.modalPrice*100
    }
}
