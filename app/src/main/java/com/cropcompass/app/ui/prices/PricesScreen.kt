package com.cropcompass.app.ui.prices

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.cropcompass.app.ui.MarketUiState
import com.cropcompass.app.ui.components.*
import com.cropcompass.app.domain.MarketLogic

@Composable fun PricesScreen(state: MarketUiState, onCommodity: (String,String) -> Unit, onDistricts: () -> Unit, map: @Composable (String?,String?,String?) -> Unit, initiallyMap: Boolean = false) {
    val data=state.snapshot; val kn=state.settings.language=="kn"
    var district by rememberSaveable(state.settings.homeDistrictId) { mutableStateOf(state.settings.homeDistrictId) }
    var mandi by rememberSaveable(state.settings.homeMandiId) { mutableStateOf(state.settings.homeMandiId) }
    var query by rememberSaveable { mutableStateOf("") }; var sort by rememberSaveable { mutableStateOf("Price high") }; var showMap by rememberSaveable { mutableStateOf(initiallyMap) }
    val markets=data.mandis.filter { district.isBlank() || it.districtId==district }
    val origin=data.mandis.find { it.id==state.settings.homeMandiId }
    val cropIds=MarketLogic.search(data.commodities,query).map { it.id }.toSet()
    val filtered=MarketLogic.latest(data.prices).filter { it.commodityId in cropIds && markets.any { m->m.id==it.mandiId } && (mandi.isBlank() || it.mandiId==mandi) }
    val sorted=when(sort) {
        "Price low" -> filtered.sortedBy { it.modalPrice }
        "Change" -> filtered.sortedByDescending { MarketLogic.change(data.prices,it) ?: Double.NEGATIVE_INFINITY }
        "Arrivals" -> filtered.sortedByDescending { it.arrivalsTonnes ?: Double.NEGATIVE_INFINITY }
        "Distance" -> filtered.sortedBy { row -> val m=data.mandis.find { it.id==row.mandiId }; if(origin?.lat!=null && origin.lng!=null && m?.lat!=null && m.lng!=null) MarketLogic.haversine(origin.lat,origin.lng,m.lat,m.lng) else Double.POSITIVE_INFINITY }
        "Date" -> filtered.sortedByDescending { it.date }
        else -> filtered.sortedByDescending { it.modalPrice }
    }
    Page {
        Heading(tr("Prices")); DataBanner(state)
        Choice(tr("District"),data.districts.find { it.id==district }?.name(kn) ?: "All Karnataka",listOf("All Karnataka")+data.districts.map { it.name(kn) },tag="price-district") { value -> district=data.districts.find { it.name(kn)==value }?.id.orEmpty(); mandi="" }
        Choice(tr("Mandi"),markets.find { it.id==mandi }?.name(kn) ?: "All mandis in district",listOf("All mandis in district")+markets.map { it.name(kn) },tag="price-mandi") { value -> mandi=markets.find { it.name(kn)==value }?.id.orEmpty() }
        OutlinedTextField(query,{query=it},label={Text(tr("Search commodities"))},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("price-search"))
        Choice("Sort",sort,listOf("Price high","Price low","Change","Arrivals","Distance","Date")){sort=it}
        Row { FilterChip(selected=!showMap,onClick={showMap=false},label={Text(tr("List"))}); FilterChip(selected=showMap,onClick={showMap=true},label={Text(tr("Map"))}) }
        TextButton(onClick=onDistricts){Text("Browse all 31 districts")}
        if(showMap) { if(filtered.isEmpty()) Notice("No matching observations for this map.") else map(mandi.ifBlank { null },MarketLogic.search(data.commodities,query).firstOrNull()?.id,district.ifBlank{null}) }
        else {
            Text("${sorted.size} matching observations",style=MaterialTheme.typography.labelMedium)
            if(sorted.isEmpty()) Notice("No matching prices. Try all mandis or another commodity.")
            sorted.take(150).forEach { price -> PriceCard(state,price){onCommodity(price.commodityId,price.mandiId)} }
            if(sorted.size>150) Text("Showing the first 150 results. Refine the filters to see more.")
        }
    }
}
