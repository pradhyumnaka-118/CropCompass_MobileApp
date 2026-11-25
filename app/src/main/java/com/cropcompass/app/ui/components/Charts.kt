package com.cropcompass.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.*
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.*
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

data class ChartSeries(val label: String, val points: List<Pair<Double,Double>>, val dotted: Boolean = false)
@Composable fun LineChart(lines: List<ChartSeries>, unit: String = "₹ per quintal", dates: Boolean = true) {
    val nonempty=lines.filter { it.points.isNotEmpty() }
    if(nonempty.isEmpty()) { Notice("No observations in this range. Missing dates are not filled."); return }
    val model=remember(nonempty) { CartesianChartModel(LineCartesianLayerModel.build { nonempty.forEach { line -> series(x=line.points.map { it.first },y=line.points.map { it.second }) } }) }
    val colors=listOf(Color(0xFF2E7D32),Color(0xFF1565C0),Color(0xFFAD6500),Color(0xFF6D4C41))
    val providers=remember(nonempty.map{it.dotted}) { nonempty.mapIndexed { i,line -> LineCartesianLayer.Line(fill=LineCartesianLayer.LineFill.single(fill(colors[i%colors.size])),stroke=if(line.dotted) LineCartesianLayer.LineStroke.Dashed(dashLengthDp=2f,gapLengthDp=4f) else LineCartesianLayer.LineStroke.Continuous()) } }
    val provider=remember(providers){LineCartesianLayer.LineProvider.series(providers)}
    Text(unit,style=MaterialTheme.typography.labelMedium)
    nonempty.forEachIndexed { i,line -> Text("● ${line.label}${if(line.dotted) " (estimate, dotted)" else ""}",color=colors[i%colors.size],style=MaterialTheme.typography.labelSmall) }
    val formatter=remember(dates) { CartesianValueFormatter { _,value,_ -> if(dates) LocalDate.ofEpochDay(value.roundToLong()).format(DateTimeFormatter.ofPattern("dd MMM")) else value.roundToLong().toString() } }
    CartesianChartHost(chart=rememberCartesianChart(rememberLineCartesianLayer(provider),startAxis=VerticalAxis.rememberStart(),bottomAxis=HorizontalAxis.rememberBottom(valueFormatter=formatter)),model=model,modifier=Modifier.fillMaxWidth().height(240.dp).testTag("line-chart"),scrollState=rememberVicoScrollState(scrollEnabled=false))
}
