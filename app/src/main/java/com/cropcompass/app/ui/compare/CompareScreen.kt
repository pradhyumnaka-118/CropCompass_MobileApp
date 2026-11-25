package com.cropcompass.app.ui.compare

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.cropcompass.app.ui.MarketUiState
import com.cropcompass.app.ui.components.*
import com.cropcompass.app.domain.*
import java.time.LocalDate
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries

@Composable fun CompareScreen(state: MarketUiState, initialCrop: String?, onCommodity: (String,String) -> Unit, map: @Composable (String,List<com.cropcompass.app.data.local.PriceRecord>) -> Unit) {
    val data=state.snapshot; val kn=state.settings.language=="kn"
    var crop by rememberSaveable(initialCrop) { mutableStateOf(initialCrop ?: data.commodities.firstOrNull()?.id.orEmpty()) }
    var quantity by rememberSaveable { mutableStateOf("20") }; var rate by rememberSaveable(state.settings.transportRate) { mutableStateOf(state.settings.transportRate.toString()) }
    var radius by rememberSaveable(state.settings.radiusKm) { mutableStateOf(if(state.settings.radiusKm>200) "All Karnataka" else state.settings.radiusKm.toInt().toString()) }
    val varieties=data.prices.filter{it.commodityId==crop}.map{it.variety}.distinct().sortedBy{it.orEmpty()}
    var variety by rememberSaveable(crop) { mutableStateOf(varieties.firstOrNull()) }
    val home=data.mandis.find{it.id==state.settings.homeMandiId}
    var origin by rememberSaveable(state.settings.homeMandiId) { mutableStateOf("My mandi") }
    var districtId by rememberSaveable(state.settings.homeDistrictId) { mutableStateOf(state.settings.homeDistrictId) }
    var originMandiId by rememberSaveable(state.settings.homeMandiId) { mutableStateOf(state.settings.homeMandiId) }
    var gps by remember { mutableStateOf<Pair<Double,Double>?>(null) }
    val originMandi=if(origin=="My mandi") home else data.mandis.find{it.id==originMandiId}
    val point=if(origin=="Current GPS") gps else originMandi?.let{if(it.lat!=null && it.lng!=null) it.lat to it.lng else null}
    var showResults by rememberSaveable { mutableStateOf(false) }
    val results=if(showResults && point!=null) runCatching { Recommendation.rank(data,crop,variety,point.first,point.second,quantity.toDoubleOrNull() ?: Double.NaN,rate.toDoubleOrNull() ?: Double.NaN,if(radius=="All Karnataka") 99999.0 else radius.toDouble()) } else null
    val ranked=results?.getOrNull().orEmpty()
    Page {
        Heading(tr("Best mandi")); DataBanner(state)
        Choice(tr("Commodity"),data.commodities.find{it.id==crop}?.name(kn).orEmpty(),data.commodities.map{it.name(kn)},tag="compare-commodity"){label->crop=data.commodities.first{it.name(kn)==label}.id;showResults=false}
        Choice("Compatible variety",variety ?: "Unspecified",varieties.map{it ?: "Unspecified"}){variety=it.takeUnless{it=="Unspecified"}}
        OutlinedTextField(quantity,{quantity=it},label={Text("Quantity (quintals)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth().testTag("compare-quantity"))
        Choice("Origin",origin,listOf("My mandi","Current GPS","Choose district")){origin=it}
        if(origin=="Current GPS") LocationPicker { lat,lng->gps=lat to lng }
        if(origin=="Choose district") {
            Choice(tr("District"),data.districts.find{it.id==districtId}?.name(kn).orEmpty(),data.districts.map{it.name(kn)},tag="origin-district"){label->districtId=data.districts.first{it.name(kn)==label}.id;originMandiId=""}
            val choices=data.mandis.filter{it.districtId==districtId}
            Choice("Origin mandi",choices.find{it.id==originMandiId}?.name(kn) ?: "Choose mandi",choices.map{it.name(kn)}){label->originMandiId=choices.first{it.name(kn)==label}.id}
        }
        Text("Origin: ${if(origin=="Current GPS") gps?.let{"${decimal(it.first)}, ${decimal(it.second)}"} ?: "Not available" else originMandi?.name(kn) ?: "Select an origin mandi"}")
        Choice("Radius (estimated road km)",radius,listOf("25","50","100","150","200","All Karnataka")){radius=it}
        OutlinedTextField(rate,{rate=it},label={Text("Transport estimate (₹ / quintal / km)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
        Text("Editable estimate. Road distance = haversine × 1.3. Transport = distance × rate × quantity.")
        Button(onClick={showResults=true},modifier=Modifier.testTag("run-compare")){Text("Calculate best mandi")}
        if(showResults && point==null) Notice("Origin coordinates are unavailable. Choose another mandi or use GPS.",true)
        results?.exceptionOrNull()?.message?.let{Notice(it,true)}
        if(results?.isSuccess==true && ranked.isEmpty()) Notice("No compatible prices within this radius from the last 30 days. Try a wider radius or another variety.")
        if(ranked.isNotEmpty()) {
            val best=ranked.first(); val highest=ranked.maxBy{it.price.modalPrice}
            Notice("Best estimated net: ${best.mandi.name(kn)} • ${money(best.net)} total"+ranked.getOrNull(1)?.let{"\n${money(best.net-it.net)} more than the second option"}.orEmpty())
            if(highest.mandi.id!=best.mandi.id) Text("Higher price at ${highest.mandi.name(kn)}, but transport reduces its net by ${money(best.net-highest.net)} compared with the best option.")
            if(ranked.map{it.price.date}.distinct().size>1) Notice("Observation dates differ. Check each date before selling.")
            ranked.take(10).forEachIndexed { i,item->
                Text("#${i+1} ${if(i==0) "Best net" else ""} ${if(item.mandi.id==highest.mandi.id) "• Highest price" else ""}",style=MaterialTheme.typography.titleMedium)
                Text("Net ${money(item.net)} • Gross ${money(item.gross)} • Transport ${money(item.transport)}\nEstimated road distance ${decimal(item.roadKm)} km",modifier=Modifier.testTag("rank-${i+1}"))
                PriceCard(state,item.price){onCommodity(crop,item.mandi.id)}
                if(item.mandi.geoConfidence=="DISTRICT") Text("Distance uses approximate coordinates.",style=MaterialTheme.typography.bodySmall)
            }
            Text("Net revenue by mandi",style=MaterialTheme.typography.titleLarge)
            NetBars(ranked.take(10))
            map(crop,ranked.map{it.price})
            if(ranked.size>=2) {
                var first by rememberSaveable(crop){mutableStateOf(ranked[0].mandi.id)}; var second by rememberSaveable(crop){mutableStateOf(ranked[1].mandi.id)}
                Text("Mandi vs mandi • 30 days",style=MaterialTheme.typography.titleLarge)
                listOf(true,false).forEach { isFirst->Choice(if(isFirst) "Mandi A" else "Mandi B",ranked.find{it.mandi.id==(if(isFirst) first else second)}?.mandi?.name(kn).orEmpty(),ranked.map{it.mandi.name(kn)}){label->val id=ranked.first{it.mandi.name(kn)==label}.mandi.id;if(isFirst) first=id else second=id} }
                val history=data.prices.filter{it.commodityId==crop && it.variety==variety && it.date>=LocalDate.now().minusDays(29).toString() && it.source==data.info?.dataSource}
                LineChart(listOf(first,second).map{id->ChartSeries(data.mandis.find{it.id==id}?.name(kn).orEmpty(),history.filter{it.mandiId==id}.sortedBy{it.date}.map{LocalDate.parse(it.date).toEpochDay().toDouble() to it.modalPrice})})
                val a=history.filter{it.mandiId==first}.associateBy{it.date};val b=history.filter{it.mandiId==second}.associateBy{it.date};val common=a.keys.intersect(b.keys)
                if(first==second) Text("Choose two different mandis.") else Text(if(common.isEmpty()) "No overlapping observation dates." else "Average gap A − B on ${common.size} matched dates: ${money(common.map{a.getValue(it).modalPrice-b.getValue(it).modalPrice}.average())} / quintal")
            }
        }
        Notice("Recommendations are estimates based on reported modal prices and an assumed transport rate. Actual selling price depends on variety, grade and quality; expenses may vary.")
    }
}
@Composable private fun NetBars(rows: List<RankedMandi>) {
    val model=remember(rows){com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModel(com.patrykandpatrick.vico.core.cartesian.data.ColumnCartesianLayerModel.build { series(x=rows.indices.map{it+1},y=rows.map{it.net}) })}
    Text("₹ total • numbered mandis match the ranking above",style=MaterialTheme.typography.bodySmall)
    val columns=ColumnCartesianLayer.ColumnProvider.series(rememberLineComponent(fill(Color(0xFF2E7D32)),16.dp))
    com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost(com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart(com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer(columns)),model,modifier=Modifier.fillMaxWidth().height(220.dp),scrollState=com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState(scrollEnabled=false))
}
