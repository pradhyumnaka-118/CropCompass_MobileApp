package com.cropcompass.app.ui.profile

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.cropcompass.app.data.repository.*
import com.cropcompass.app.ui.MarketUiState
import com.cropcompass.app.ui.components.*

@Composable fun AboutDataScreen(state:MarketUiState){
    val info=state.snapshot.info
    Page{
        Heading(tr("About the data"));DataBanner(state)
        Text("Source: ${info?.source ?: "Not loaded"}")
        if(info?.dataSource=="fixture")Notice("Prices, arrivals, mandi identities and coordinate positions are synthetic test values. No Agmarknet CSV was supplied.")
        Text("Observation dates: ${info?.firstDate} to ${info?.lastDate}\nRecords: ${info?.recordCount}\nDistricts: ${state.snapshot.districts.size}\nMandis: ${state.snapshot.mandis.size}\nCommodities: ${state.snapshot.commodities.size}\nBundle generated: ${info?.generatedAt}\nData version: ${info?.dataVersion}")
        Text("Real data origin: Agmarknet via data.gov.in, imported from the supplied CSV. Farmer reports are separate and unverified. Prices are ₹ per quintal; arrivals are tonnes where supplied.")
        Notice("Transport rate ${money(state.settings.transportRate)} / quintal / km is an editable assumption. Road distance = straight-line distance × 1.3, not a route-planner measurement.")
        Text("Charts contain only available observations. Forecasts are estimates with rolling backtests. Optional Spark cards appear only when files match the installed data version.")
        Text("Map: © OpenStreetMap contributors (ODbL); osmdroid. Background tiles need internet or a cache. Approximate coordinates may be district centroids; test coordinates do not identify real mandis.")
    }
}
@Composable fun InsightsScreen(state:MarketUiState){
    val context=LocalContext.current;var retry by remember{mutableIntStateOf(0)}
    var result by remember { mutableStateOf<AnalyticsResult?>(null) }
    LaunchedEffect(state.snapshot.info?.dataVersion,retry){result=AnalyticsParser.load(context,state.snapshot)}
    Page{
        Heading(tr("Market insights"));DataBanner(state)
        if(result==null){CircularProgressIndicator();Text("Reading available insights…")}
        result?.let{data->
            if(data.documents.isEmpty() && data.errors.isEmpty())Notice("No Spark insight files have been supplied. Market browsing, comparisons and on-device estimates work independently.")
            data.errors.forEach{Notice(it,true)}
            if(data.errors.isNotEmpty())OutlinedButton(onClick={retry++}){Text(tr("Retry"))}
            data.documents.forEach{doc->
                Text(when(doc.kind){"mandi_volatility"->"Mandi price volatility";"district_price_dispersion"->"District price dispersion";"top_commodity_by_district"->"Top crop by district arrivals";else->"Estimated category trade value"},style=MaterialTheme.typography.titleLarge)
                Text("${doc.start} to ${doc.end} • supplied Spark analysis")
                if(doc.rows.isEmpty())Text("No observations in this insight.")
                doc.rows.take(25).forEach{row->
                    fun name(key:String)=row.get(key)?.asString.orEmpty()
                    fun amount(key:String)=money(row.get(key).asDouble)
                    val commodity=state.snapshot.commodities.find{it.id==name("commodityId")}?.name(state.settings.language=="kn").orEmpty()
                    val district=state.snapshot.districts.find{it.id==name("districtId")}?.name(state.settings.language=="kn").orEmpty()
                    val mandi=state.snapshot.mandis.find{it.id==name("mandiId")}?.name(state.settings.language=="kn").orEmpty()
                    Notice(when(doc.kind){
                        "mandi_volatility"->"$mandi • $commodity\nMean ${amount("meanModalPrice")} / quintal; price standard deviation ${amount("stddevPrice")} / quintal\n${name("observationCount")} observations • ${doc.start} to ${doc.end}"
                        "district_price_dispersion"->"$district • $commodity • ${name("mandiCount")} mandis\nMin ${amount("minModalPrice")} / median ${amount("medianModalPrice")} / max ${amount("maxModalPrice")} per quintal\n${doc.start} to ${doc.end}"
                        "top_commodity_by_district"->"$district • $commodity\n${name("arrivalsTonnes")} tonnes across ${name("observationCount")} observations"
                        else->"${name("category")}\n${amount("estimatedValueInr")} estimated total trade value; ${name("recordsUsed")} observations. This is not recorded sales revenue."
                    })
                }
                if(doc.rows.size>25)Text("Showing the first 25 rows of ${doc.rows.size}.")
            }
        }
    }
}
