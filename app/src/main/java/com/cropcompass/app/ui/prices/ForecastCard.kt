package com.cropcompass.app.ui.prices

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.cropcompass.app.data.local.PriceRecord
import com.cropcompass.app.domain.*
import com.cropcompass.app.ui.MarketUiState
import com.cropcompass.app.ui.components.*
import java.time.LocalDate

@Composable fun ForecastCard(state:MarketUiState,records:List<PriceRecord>){
    Text("7-day estimate",style=MaterialTheme.typography.titleLarge)
    Notice("Estimate, not advice. A simple on-device trend model; these are not reported prices.")
    val result=remember(records){LinearTrendForecaster().forecast(records.map{Observation(LocalDate.parse(it.date),it.modalPrice)})}
    if(result==null){Text("At least 7 usable observations in a 30-day window are needed.");return}
    if(MarketLogic.stale(result.anchor.toString())){Text("Latest observation is stale. A current estimate is not shown.");return}
    val market=state.snapshot.mandis.find{it.id==records.firstOrNull()?.mandiId}?.name(state.settings.language=="kn").orEmpty()
    Text("$market • ${result.estimates.first().date} to ${result.estimates.last().date} • ₹ per quintal")
    val observed=records.takeLast(14).map{LocalDate.parse(it.date).toEpochDay().toDouble() to it.modalPrice}
    LineChart(listOf(ChartSeries("Observed modal",observed),ChartSeries("7-day estimate",result.estimates.map{it.date.toEpochDay().toDouble() to it.price},true)))
    Text(result.mae?.let{"Backtest MAE: ${money(it)} / quintal on ${result.backtestCount} observed dates in the last 14 days. Each prediction uses only earlier data."} ?: "Insufficient earlier observations for a backtest.")
    result.estimates.forEach{Text("${it.date}: ${money(it.price)} / quintal • $market • estimate")}
}
