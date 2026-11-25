package com.cropcompass.app

import com.cropcompass.app.data.local.*
import com.cropcompass.app.data.repository.MarketSnapshot
import com.cropcompass.app.domain.Recommendation
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RecommendationTest {
    private val today=LocalDate.of(2026,11,5)
    private fun data()=MarketSnapshot(mandis=listOf(Mandi("a","A",districtId="d",lat=13.0,lng=77.0,geoConfidence="EXACT"),Mandi("b","B",districtId="d",lat=14.0,lng=77.0,geoConfidence="EXACT")),prices=listOf(PriceRecord("a","a","c","v","2026-11-05",1000.0,1100.0,1050.0,null),PriceRecord("b","b","c","v","2026-11-05",1100.0,1300.0,1200.0,null)),info=DataInfo(dataVersion="1",dataSource="fixture",source="test",firstDate="2026-11-05",lastDate="2026-11-05",recordCount=2,historyDays=1,generatedAt="2026-11-05",unit="INR_QUINTAL"))
    @Test fun highTransportCanOutweighHighestPrice(){val result=Recommendation.rank(data(),"c","v",13.0,77.0,20.0,2.0,99999.0,today);assertEquals("a",result.first().mandi.id);assertEquals(21000.0,result.first().net,.01);assertTrue(result[1].transport>0)}
    @Test fun lowerTransportMakesHigherPriceWin(){val result=Recommendation.rank(data(),"c","v",13.0,77.0,20.0,.05,99999.0,today);assertEquals("b",result.first().mandi.id);assertEquals(24000.0,result.first().gross,.01)}
    @Test fun invalidQuantityAndMissingPrices(){assertThrows(IllegalArgumentException::class.java){Recommendation.rank(data(),"c","v",13.0,77.0,-1.0,.05,200.0,today)}; assertTrue(Recommendation.rank(data(),"missing","v",13.0,77.0,20.0,.05,200.0,today).isEmpty())}
    @Test fun excludesStaleIncompatibleAndWrongSource(){val original=data();val d=original.copy(prices=original.prices.map{it.copy(date="2026-09-01")});assertTrue(Recommendation.rank(d,"c","v",13.0,77.0,20.0,.05,200.0,today).isEmpty());assertTrue(Recommendation.rank(original,"c","other",13.0,77.0,20.0,.05,200.0,today).isEmpty());assertTrue(Recommendation.rank(original.copy(prices=original.prices.map{it.copy(source="unverified")}),"c","v",13.0,77.0,20.0,.05,200.0,today).isEmpty())}
}
