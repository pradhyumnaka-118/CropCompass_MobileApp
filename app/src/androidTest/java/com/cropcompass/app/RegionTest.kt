package com.cropcompass.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.cropcompass.app.data.local.Preferences
import kotlinx.coroutines.runBlocking
import org.junit.*

class RegionTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var activity: ActivityScenario<MainActivity>
    @Before fun setup() {
        requireFixture()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { Preferences(context).guest(true); Preferences(context).configure("en",.05,200.0); Preferences(context).resetOnboarding() }
        activity=ActivityScenario.launch(MainActivity::class.java)
    }
    @After fun close(){if(::activity.isInitialized)activity.close()}
    @Test fun districtFiltersMandisAndPersistsSelection() {
        compose.waitUntil(20000){compose.onAllNodesWithTag("select-District").fetchSemanticsNodes().isNotEmpty()}
        screenshot("onboarding")
        compose.onNodeWithTag("select-District").performClick()
        compose.onNodeWithText("Mandya").performScrollTo().performClick()
        compose.onNodeWithTag("select-Mandi").performClick()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val lastDate=runBlocking{(context.applicationContext as CropCompassApplication).container.market.dao.currentInfo()!!.lastDate}
        compose.onNodeWithText("TEST Market 3 (synthetic) • $lastDate").performClick()
        compose.onAllNodesWithText("TEST Market 1 (synthetic) • $lastDate").assertCountEquals(0)
        compose.onNodeWithTag("save-region").performScrollTo().performClick()
        compose.waitUntil(15000){compose.onAllNodesWithText("Your mandi: TEST Market 3 (synthetic) • Mandya").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithTag("select-District").assertTextContains("Mandya")
        compose.onNodeWithTag("select-Mandi").assertTextContains("TEST Market 3 (synthetic)",substring=true)
        screenshot("profile")
    }
}
