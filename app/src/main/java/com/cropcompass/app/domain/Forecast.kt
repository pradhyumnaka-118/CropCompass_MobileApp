package com.cropcompass.app.domain

import java.time.LocalDate
import kotlin.math.abs

data class Observation(val date: LocalDate,val price: Double)
data class ForecastResult(val estimates: List<Observation>,val mae: Double?,val backtestCount: Int,val anchor: LocalDate)
interface PriceForecaster { fun forecast(history: List<Observation>): ForecastResult? }
/** A 30-calendar-day linear fit to 3-observation averages; no filled daily observations. */
class LinearTrendForecaster:PriceForecaster {
    private data class Fit(val x:Double,val y:Double,val slope:Double){fun predict(date:LocalDate)=(y+slope*(date.toEpochDay()-x)).coerceAtLeast(0.0)}
    private fun fit(history:List<Observation>):Fit? {
        if(history.size<7)return null
        val since=history.last().date.minusDays(29)
        val window=history.filter{it.date>=since}
        if(window.size<7)return null
        val smooth=window.windowed(3).map{points->points.map{it.date.toEpochDay().toDouble()}.average() to points.map{it.price}.average()}
        val x=smooth.map{it.first}.average();val y=smooth.map{it.second}.average()
        val denominator=smooth.sumOf{(it.first-x)*(it.first-x)}
        val slope=if(denominator>0)smooth.sumOf{(it.first-x)*(it.second-y)}/denominator else 0.0
        return if(x.isFinite() && y.isFinite() && slope.isFinite())Fit(x,y,slope)else null
    }
    override fun forecast(history: List<Observation>):ForecastResult? {
        val observed=history.filter{it.price.isFinite() && it.price>0}.sortedBy{it.date}.distinctBy{it.date}
        val model=fit(observed) ?: return null
        val anchor=observed.last().date
        val predictions=(1L..7L).map{day->Observation(anchor.plusDays(day),model.predict(anchor.plusDays(day)))}
        if(predictions.any{!it.price.isFinite()})return null
        val errors=observed.filter{it.date>anchor.minusDays(14)}.mapNotNull{target->fit(observed.filter{it.date<target.date})?.predict(target.date)?.let{abs(it-target.price)}}
        return ForecastResult(predictions,errors.takeIf{it.isNotEmpty()}?.average(),errors.size,anchor)
    }
}
