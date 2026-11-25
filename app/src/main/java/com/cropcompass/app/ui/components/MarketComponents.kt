package com.cropcompass.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cropcompass.app.data.local.PriceRecord
import com.cropcompass.app.domain.MarketLogic
import com.cropcompass.app.ui.MarketUiState
import java.text.NumberFormat
import java.util.Locale

fun money(value: Double): String = NumberFormat.getCurrencyInstance(Locale("en","IN")).apply { maximumFractionDigits=2 }.format(value)
fun decimal(value: Double): String = String.format(Locale.ENGLISH,"%.1f",value)
@Composable fun DataBanner(state: MarketUiState) {
    if(!state.online) Notice("Offline • using observations cached on this device. They may be outdated.")
    if(state.snapshot.info?.dataSource=="fixture") Notice(tr("TEST DATA — not real prices"))
    else Text("Agmarknet • Data as of ${state.snapshot.info?.lastDate ?: "—"}",style=MaterialTheme.typography.labelMedium)
}
@Composable fun PriceCard(state: MarketUiState, price: PriceRecord, onClick: () -> Unit = {}) {
    val data=state.snapshot; val kn=state.settings.language=="kn"
    val mandi=data.mandis.find { it.id==price.mandiId }
    val crop=data.commodities.find { it.id==price.commodityId }
    OutlinedCard(onClick=onClick,modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(crop?.name(kn) ?: price.commodityId,style=MaterialTheme.typography.titleMedium)
            Text("${money(price.modalPrice)} / quintal",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary)
            Text("${mandi?.name(kn) ?: price.mandiId} • ${data.districts.find { it.id==mandi?.districtId }?.name(kn).orEmpty()}")
            Text("${price.date} • ₹ per quintal • ${price.variety ?: "Variety unspecified"}",style=MaterialTheme.typography.bodySmall)
            Text("Min ${price.minPrice?.let(::money) ?: "—"}   Max ${price.maxPrice?.let(::money) ?: "—"}   Arrivals ${price.arrivalsTonnes?.let(::decimal) ?: "—"} t",style=MaterialTheme.typography.bodySmall)
            val change=MarketLogic.change(data.prices,price)
            if(change!=null) Text("${if(change>=0) "↑" else "↓"} ${decimal(kotlin.math.abs(change))}% vs previous report",style=MaterialTheme.typography.labelMedium)
            if(MarketLogic.stale(price.date)) Text(tr("Stale")+" • older than 7 days",color=MaterialTheme.colorScheme.error)
            Text(if(price.source=="fixture") tr("TEST DATA — not real prices") else "Agmarknet / data.gov.in",style=MaterialTheme.typography.labelSmall)
        }
    }
}
