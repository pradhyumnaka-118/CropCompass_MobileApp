package com.cropcompass.app.domain

import com.cropcompass.app.data.local.*
import com.cropcompass.app.data.repository.MarketSnapshot
import java.time.LocalDate

data class RankedMandi(val mandi: Mandi, val price: PriceRecord, val roadKm: Double, val transport: Double, val gross: Double, val net: Double)
object Recommendation {
    fun rank(data: MarketSnapshot, commodity: String, variety: String?, lat: Double, lng: Double, quantity: Double, rate: Double, radius: Double, today: LocalDate = LocalDate.now()): List<RankedMandi> {
        require(quantity.isFinite() && quantity>0 && quantity<=10_000_000) { "Enter a positive quantity up to 10 million quintals." }
        require(rate.isFinite() && rate>=0 && rate<=1000) { "Enter a transport rate between 0 and 1000." }
        require(radius.isFinite() && radius>0) { "Select a search radius." }
        require(lat.isFinite() && lat in -90.0..90.0 && lng.isFinite() && lng in -180.0..180.0) { "Choose an origin with valid coordinates." }
        val candidates=MarketLogic.latest(data.prices.filter { it.commodityId==commodity && it.variety==variety && it.unit=="INR_QUINTAL" && it.source==data.info?.dataSource && it.modalPrice.isFinite() && it.modalPrice>0 && MarketLogic.age(it.date,today) in 0..30 })
        return candidates.mapNotNull { price ->
            val mandi=data.mandis.find { it.id==price.mandiId } ?: return@mapNotNull null
            if(mandi.lat==null || mandi.lng==null) return@mapNotNull null
            val distance=MarketLogic.haversine(lat,lng,mandi.lat,mandi.lng)*1.3
            if(distance>radius) return@mapNotNull null
            val transport=distance*rate*quantity; val gross=price.modalPrice*quantity
            require(transport.isFinite() && gross.isFinite() && (gross-transport).isFinite()){ "Price or cost exceeds the supported calculation range." }
            RankedMandi(mandi,price,distance,transport,gross,gross-transport)
        }.sortedWith(compareByDescending<RankedMandi>{it.net}.thenBy{it.roadKm}.thenBy{it.mandi.id})
    }
}
