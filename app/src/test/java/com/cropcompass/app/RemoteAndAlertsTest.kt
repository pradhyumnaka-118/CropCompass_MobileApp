package com.cropcompass.app

import com.cropcompass.app.data.Alerts
import com.cropcompass.app.data.remote.*
import com.cropcompass.app.data.local.*
import com.cropcompass.app.data.repository.MarketSnapshot
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.*
import retrofit2.Retrofit
import retrofit2.HttpException
import retrofit2.converter.gson.GsonConverterFactory
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RemoteAndAlertsTest {
    @Test fun targetDirections(){assertTrue(Alerts.condition(10.0,10.0,"ABOVE"));assertTrue(Alerts.condition(9.0,10.0,"BELOW"));assertFalse(Alerts.condition(9.0,10.0,"ABOVE"))}
    @Test fun apiUsesKarnatakaFilterAndPropagatesFailure()=runTest {
        val server=MockWebServer();server.start()
        try {server.enqueue(MockResponse().setResponseCode(503));val api=Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(DataGovApi::class.java)
            try {api.prices("configured-resource","TEST_KEY");fail("Expected HTTP failure")}catch(e:HttpException){assertEquals(503,e.code())}
            val request=server.takeRequest();assertEquals("Karnataka",request.requestUrl!!.queryParameter("filters[state]"));assertEquals("/resource/configured-resource",request.requestUrl!!.encodedPath)
        } finally {server.shutdown()}
    }
    @Test fun rejectsUnmatchedWrongStateAndUnits(){
        val data=MarketSnapshot(districts=listOf(District("kolar","Kolar")),mandis=listOf(Mandi("code1","Mandi A",districtId="kolar",lat=13.0,lng=78.0,geoConfidence="EXACT")),commodities=listOf(Commodity("tomato","Tomato")))
        val row=mapOf("state" to "Karnataka","district" to "Kolar","market" to "Mandi A","commodity" to "Tomato","arrival_date" to "05/11/2026","modal_price" to "1800","min_price" to "1700","max_price" to "1900")
        assertEquals(1800.0,DataGovMapper.map(row,data,LocalDate.of(2026,11,6))!!.modalPrice,0.0)
        assertNull(DataGovMapper.map(row+("state" to "Other"),data));assertNull(DataGovMapper.map(row+("market" to "Unknown"),data));assertNull(DataGovMapper.map(row+("price_unit" to "Rs/Kg"),data))
    }
}
