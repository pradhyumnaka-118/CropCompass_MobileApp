package com.cropcompass.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.cropcompass.app.data.local.*
import com.cropcompass.app.data.repository.MarketRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class SeedingTest {
    @Test fun reseedingPreservesPersonalRecords() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,MarketDatabase::class.java).build()
        try {
            val repo=MarketRepository(context,db); repo.seed()
            val expected=db.dao().currentInfo()!!.recordCount
            assertEquals(expected,db.dao().prices().first().size)
            assertTrue(db.dao().districts().first().size>=31)
            db.dao().watch(WatchlistItem("seed-test","tomato","test-1",2000.0,"ABOVE"))
            db.dao().report(FarmerReport("seed-report","test-1","tomato",1000.0,observationDate="2026-11-05"))
            db.dao().info(db.dao().currentInfo()!!.copy(dataVersion="previous-bundle"))
            repo.seed()
            assertEquals("seed-test",db.dao().watchlist().first().single().id)
            assertEquals("seed-report",db.dao().reports().first().single().id)
            assertEquals(expected,db.dao().prices().first().size)
        } finally { db.close() }
    }
}
