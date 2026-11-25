package com.cropcompass.app.ui.prices

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.cropcompass.app.ui.MarketUiState
import com.cropcompass.app.ui.MarketViewModel
import com.cropcompass.app.ui.components.*
import com.cropcompass.app.domain.MarketLogic
import java.time.LocalDate

@Composable fun CommodityScreen(state: MarketUiState, commodityId: String, initialMandi: String, onMandi: (String) -> Unit, onWatch: (String,String) -> Unit, onCompare: (String) -> Unit, onReport: (String,String) -> Unit, forecast: @Composable (List<com.cropcompass.app.data.local.PriceRecord>) -> Unit = {}) {
    val data=state.snapshot; val kn=state.settings.language=="kn"; val crop=data.commodities.find { it.id==commodityId }
    var mandiId by rememberSaveable(commodityId,initialMandi) { mutableStateOf(initialMandi) }
    val available=data.mandis.filter { market->data.prices.any { it.mandiId==market.id && it.commodityId==commodityId } }
    val all=data.prices.filter { it.commodityId==commodityId && it.source==data.info?.dataSource }
    val varieties=all.map { it.variety.orEmpty() }.distinct().sorted()
    var variety by rememberSaveable(commodityId) { mutableStateOf(varieties.firstOrNull().orEmpty()) }
    var days by rememberSaveable { mutableIntStateOf(30) }
    var chartTab by rememberSaveable { mutableStateOf("Price") }
    val observations=all.filter { it.mandiId==mandiId && it.variety.orEmpty()==variety }.sortedBy { it.date }
    val since=LocalDate.now().minusDays(days.toLong()-1).toString()
    val window=observations.filter { it.date>=since }
    Page {
        Heading(crop?.name(kn) ?: "Commodity",data.mandis.find { it.id==mandiId }?.name(kn).orEmpty()); DataBanner(state)
        Choice(tr("Mandi"),available.find { it.id==mandiId }?.name(kn) ?: "Choose mandi",available.map { it.name(kn) },tag="detail-mandi"){label->mandiId=available.first{it.name(kn)==label}.id}
        Choice("Variety",variety.ifBlank { "Unspecified" },varieties.map { it.ifBlank { "Unspecified" } }){variety=if(it=="Unspecified") "" else it}
        observations.lastOrNull()?.let { PriceCard(state,it){onMandi(mandiId)} } ?: Notice("No observations at this mandi for this variety.")
        Text("Price history",style=MaterialTheme.typography.titleLarge)
        Row { listOf("Price","Arrivals","Estimate").forEach { tab->FilterChip(selected=chartTab==tab,onClick={chartTab=tab},label={Text(tab)},modifier=Modifier.testTag("chart-tab-$tab")) } }
        if(chartTab!="Estimate") {
            Row { listOf(7,30,90).forEach { range->FilterChip(selected=days==range,onClick={days=range},label={Text("$range days")},modifier=Modifier.testTag("range-$range")) } }
            Text("${data.mandis.find{it.id==mandiId}?.name(kn)} • ${window.firstOrNull()?.date ?: "—"} to ${window.lastOrNull()?.date ?: "—"} • ${if(chartTab=="Price") "₹ per quintal" else "tonnes"}")
        }
        key(chartTab) {
            when(chartTab) {
                "Price" -> {
                    LineChart(listOf(ChartSeries("Minimum",window.mapNotNull { p->p.minPrice?.let { LocalDate.parse(p.date).toEpochDay().toDouble() to it } }),ChartSeries("Modal",window.map { LocalDate.parse(it.date).toEpochDay().toDouble() to it.modalPrice }),ChartSeries("Maximum",window.mapNotNull { p->p.maxPrice?.let { LocalDate.parse(p.date).toEpochDay().toDouble() to it } })))
                    if(window.isNotEmpty()) Text("Modal: min ${money(window.minOf{it.modalPrice})} • max ${money(window.maxOf{it.modalPrice})} • avg ${money(window.map{it.modalPrice}.average())} / quintal")
                }
                "Arrivals" -> LineChart(listOf(ChartSeries("Reported arrivals",window.mapNotNull { p->p.arrivalsTonnes?.let { LocalDate.parse(p.date).toEpochDay().toDouble() to it } })),unit="Tonnes • ${data.mandis.find{it.id==mandiId}?.name(kn)}")
                else -> forecast(observations)
            }
        }
        Button(onClick={onWatch(commodityId,mandiId)},modifier=Modifier.testTag("add-watch")){Text("Add to watchlist")}
        OutlinedButton(onClick={onCompare(commodityId)}){Text(tr("Compare"))}
        OutlinedButton(onClick={onReport(commodityId,mandiId)}){Text(tr("Report a price"))}
        Text("Across Karnataka • same variety",style=MaterialTheme.typography.titleLarge)
        MarketLogic.latest(all.filter { it.variety.orEmpty()==variety }).sortedByDescending { it.modalPrice }.forEach { price->PriceCard(state,price){onMandi(price.mandiId)} }
    }
}

@Composable fun DistrictsScreen(state: MarketUiState, onDistrict: (String) -> Unit) {
    Page {
        Heading("Karnataka districts"); DataBanner(state)
        state.snapshot.districts.forEach { district->
            OutlinedCard(onClick={onDistrict(district.id)},modifier=Modifier.fillMaxWidth()) {
                ListItem(headlineContent={Text(district.name(state.settings.language=="kn"))},supportingContent={Text("${state.snapshot.mandis.count{it.districtId==district.id}} mandis in supplied data")})
            }
        }
    }
}
@Composable fun DistrictScreen(state: MarketUiState, id: String, onMandi: (String) -> Unit) {
    val markets=state.snapshot.mandis.filter { it.districtId==id }
    Page {
        Heading(state.snapshot.districts.find{it.id==id}?.name(state.settings.language=="kn") ?: "District"); DataBanner(state)
        if(markets.isEmpty()) Notice("No mandi data was supplied for this district.")
        markets.forEach { m->OutlinedCard(onClick={onMandi(m.id)},modifier=Modifier.fillMaxWidth()) { ListItem(headlineContent={Text(m.name(state.settings.language=="kn"))},supportingContent={Text("Last report: ${m.lastReportDate ?: "No recent data"}")}) } }
    }
}
@Composable fun MandiScreen(state: MarketUiState, id: String, vm: MarketViewModel, onCommodity: (String,String) -> Unit, onHome: () -> Unit) {
    val m=state.snapshot.mandis.find { it.id==id }; val context=LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    Page {
        Heading(m?.name(state.settings.language=="kn") ?: "Mandi"); DataBanner(state)
        if(m==null) { Notice("This mandi is no longer present in the supplied dataset."); return@Page }
        Text(state.snapshot.districts.find{it.id==m.districtId}?.name(state.settings.language=="kn").orEmpty())
        Text("Coordinates: ${m.lat ?: "unknown"}, ${m.lng ?: "unknown"}\nConfidence: ${m.geoConfidence} ${if(m.geoConfidence=="DISTRICT") "• approximate location" else ""}\nLast report: ${m.lastReportDate ?: "No recent data"}")
        Button(onClick={vm.region(m.districtId,m.id);onHome()}){Text("Set as my mandi")}
        OutlinedButton(enabled=m.lat!=null && m.lng!=null && state.snapshot.info?.dataSource!="fixture",onClick={try {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("geo:${m.lat},${m.lng}?q=${m.lat},${m.lng}(${Uri.encode(m.nameEn)})")))}catch(_:Exception){error="No maps application is installed."}}){Text("Navigate")}
        if(state.snapshot.info?.dataSource=="fixture") Text("Navigation is disabled for synthetic test coordinates.")
        error?.let { Notice(it,true) }
        Text("Crops traded",style=MaterialTheme.typography.titleLarge)
        val prices=MarketLogic.latest(state.snapshot.prices.filter { it.mandiId==id })
        if(prices.isEmpty()) Notice("No prices available for this mandi.")
        prices.forEach { price->PriceCard(state,price){onCommodity(price.commodityId,id)} }
        Text("Farmer-reported, unverified",style=MaterialTheme.typography.titleLarge)
        val reports=state.reports.filter { it.mandiId==id && !it.deleted && (it.ownerUid=="guest" || it.ownerUid==(vm.container.accounts.uid ?: "guest")) }
        if(reports.isEmpty()) Text("No farmer reports saved on this device for this mandi.")
        reports.forEach { report->Notice("Farmer-reported, unverified\n${state.snapshot.commodities.find{it.id==report.commodityId}?.nameEn} • ${money(report.price)} / quintal\n${m.nameEn} • ${report.observationDate}\n${report.note.orEmpty()}") }
    }
}
