package com.cropcompass.app

import com.cropcompass.app.data.local.*
import com.cropcompass.app.data.repository.Csv
import com.cropcompass.app.domain.MarketLogic
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MarketLogicTest {
    @Test fun englishAndKannadaSearch() {
        val crops = listOf(Commodity("tomato", "Tomato", "ಟೊಮೇಟೊ"), Commodity("onion", "Onion"))
        assertEquals("tomato", MarketLogic.search(crops," TOM ").single().id)
        assertEquals("tomato", MarketLogic.search(crops,"ಟೊ").single().id)
        assertTrue(MarketLogic.search(crops,"no crop").isEmpty())
    }
    @Test fun staleBoundary() {
        val now = LocalDate.of(2026,11,5)
        assertFalse(MarketLogic.stale("2026-10-29",now)); assertTrue(MarketLogic.stale("2026-10-28",now))
    }
    @Test fun distances() {
        assertEquals(0.0,MarketLogic.haversine(13.0,77.0,13.0,77.0),1e-8)
        assertEquals(111.195,MarketLogic.haversine(0.0,0.0,1.0,0.0),.01)
    }
    @Test fun quotedCsvAndValidation() {
        val rows=Csv.rows("a,b,c\r\n1,\"a,b\",\"line\nquote\"\"\"\r\n".reader()).toList()
        assertEquals(listOf("1","a,b","line\nquote\""),rows[1])
        val valid=mapOf("id" to "p","mandiId" to "m","commodityId" to "c","date" to "2026-11-05","modalPrice" to "10")
        assertEquals(10.0,Csv.price(valid,"fixture").modalPrice,0.0)
        assertThrows(IllegalArgumentException::class.java) { Csv.price(valid + ("modalPrice" to "-1"),"fixture") }
    }
}
