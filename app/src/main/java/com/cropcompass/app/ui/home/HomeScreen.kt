package com.cropcompass.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.cropcompass.app.ui.MarketUiState
import com.cropcompass.app.ui.MarketViewModel
import com.cropcompass.app.ui.components.*
import com.cropcompass.app.domain.MarketLogic
import java.time.LocalDate
import kotlin.math.abs

@Composable fun HomeScreen(state: MarketUiState, vm: MarketViewModel, onCommodity: (String,String) -> Unit, onCompare: () -> Unit, onWatch: () -> Unit, onReports: () -> Unit, onMap: () -> Unit, onDistricts: () -> Unit) {
    val data=state.snapshot; val kn=state.settings.language=="kn"
    val home=data.mandis.find { it.id==state.settings.homeMandiId }
    var query by rememberSaveable { mutableStateOf("") }
    val latest=MarketLogic.latest(data.prices)
    val local=latest.filter { it.mandiId==home?.id }
    val suggestions=MarketLogic.search(data.commodities,query)
    fun open(id: String) { vm.recent(id); onCommodity(id,home?.id.orEmpty()) }
    Page {
        Text("KARNATAKA • FARMER FIRST",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge)
        Heading("Welcome to Crop Compass!")
        Text("${tr("Your mandi")}: ${home?.name(kn)} • ${data.districts.find { it.id==home?.districtId }?.name(kn)}")
        Text("Data as of ${data.info?.lastDate ?: "—"}",style=MaterialTheme.typography.labelMedium)
        DataBanner(state)
        OutlinedTextField(query,{query=it},label={Text(tr("Search commodities"))},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("commodity-search"))
        if(query.isNotBlank()) {
            if(suggestions.isEmpty()) Notice("No matching crops. Search in English or Kannada.")
            suggestions.take(20).forEach { crop->
                OutlinedCard(onClick={open(crop.id)},modifier=Modifier.fillMaxWidth().testTag("suggestion-${crop.id}")) {
                    ListItem(headlineContent={Text(crop.name(kn))},supportingContent={
                        val p=local.filter{it.commodityId==crop.id}.maxByOrNull{it.date}
                        Text(if(p==null) "No price at your mandi" else "${money(p.modalPrice)} / quintal • ${home?.name(kn)} • ${p.date}")
                    })
                }
            }
        } else if(state.settings.recentSearches.isNotEmpty()) {
            Text("Recent searches",style=MaterialTheme.typography.titleSmall)
            state.settings.recentSearches.forEach { id->data.commodities.find { it.id==id }?.let { crop->TextButton(onClick={open(id)}){Text(crop.name(kn))} } }
        }
        Text(tr("Today at your mandi"),style=MaterialTheme.typography.titleLarge)
        if(local.isEmpty()) Notice("No observations at your selected mandi. Explore other mandis or import a more complete CSV.")
        local.take(12).forEach { price->PriceCard(state,price){open(price.commodityId)} }
        Row { FilledTonalButton(onClick=onCompare){Text(tr("Compare"))}; Spacer(Modifier.weight(1f)); OutlinedButton(onClick=onMap){Text(tr("Map"))} }
        OutlinedButton(onClick=onWatch,modifier=Modifier.fillMaxWidth()){Text("Add to watchlist")}
        OutlinedButton(onClick=onReports,modifier=Modifier.fillMaxWidth()){Text(tr("Report a price"))}
        TextButton(onClick=onDistricts){Text("Browse districts and mandis")}
        Text("Biggest movers in Karnataka • last 7 days",style=MaterialTheme.typography.titleLarge)
        val since=LocalDate.now().minusDays(7).toString()
        val movers=latest.filter { it.date>=since }.mapNotNull { p->
            val before=data.prices.filter { it.mandiId==p.mandiId && it.commodityId==p.commodityId && it.variety==p.variety && it.source==p.source && it.date>=since && it.date<p.date }.minByOrNull{it.date}
            before?.let { Triple(p,(p.modalPrice-it.modalPrice)/it.modalPrice*100,it.date) }
        }.sortedByDescending { abs(it.second) }.take(3)
        if(movers.isEmpty()) Text("Not enough observations from the last 7 days.")
        movers.forEach { (price,change,baseline)->
            Text("${if(change>=0) "↑" else "↓"} ${decimal(abs(change))}% since $baseline")
            PriceCard(state,price){onCommodity(price.commodityId,price.mandiId)}
        }
        Text(tr("Watchlist"),style=MaterialTheme.typography.titleLarge)
        val owner=vm.container.accounts.uid ?: "guest"
        val watches=state.watches.filter { it.ownerUid==owner }
        if(watches.isEmpty()) Text("No price targets saved. Add a commodity to start watching.")
        watches.mapNotNull { watch->latest.find { it.commodityId==watch.commodityId && (watch.mandiId==null || it.mandiId==watch.mandiId) }?.let { watch to it } }.sortedBy{abs(it.second.modalPrice-it.first.targetPrice)}.take(3).forEach { (watch,price)->
            Text("Target ${money(watch.targetPrice)} / quintal • ${watch.direction}")
            PriceCard(state,price){onCommodity(price.commodityId,price.mandiId)}
        }
    }
}
