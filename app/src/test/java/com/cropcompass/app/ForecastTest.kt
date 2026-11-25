package com.cropcompass.app

import com.cropcompass.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ForecastTest{
    private val model=LinearTrendForecaster();private val start=LocalDate.of(2026,9,1)
    @Test fun constantHistoryHasZeroMae(){val r=model.forecast((0L..44L).map{Observation(start.plusDays(it),1500.0)})!!;assertEquals(7,r.estimates.size);assertEquals(1500.0,r.estimates.first().price,.001);assertEquals(0.0,r.mae!!,.001);assertEquals(14,r.backtestCount)}
    @Test fun linearSignalIsExtrapolatedWithoutLookahead(){val r=model.forecast((0L..44L).map{Observation(start.plusDays(it),1000.0+10*it)})!!;assertEquals(1450.0,r.estimates.first().price,.001);assertEquals(1510.0,r.estimates.last().price,.001);assertEquals(0.0,r.mae!!,.001)}
    @Test fun missingDaysStayMissingAndShortHistoryHasNoForecast(){assertNull(model.forecast((0L..3L).map{Observation(start.plusDays(it),1000.0)}));val history=(0L..50L).filter{it%3L!=0L}.map{Observation(start.plusDays(it),1200.0)};val r=model.forecast(history)!!;assertEquals(history.last().date.plusDays(1),r.estimates.first().date);assertTrue(r.backtestCount<14);assertEquals(0.0,r.mae!!,.001)}
}
