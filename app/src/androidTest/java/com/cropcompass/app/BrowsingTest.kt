package com.cropcompass.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.cropcompass.app.data.local.Preferences
import kotlinx.coroutines.runBlocking
import org.junit.*

class BrowsingTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var activity: ActivityScenario<MainActivity>
    @Before fun setup() {
        requireFixture()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { Preferences(context).guest(true); Preferences(context).region("kolar","test-1"); Preferences(context).configure("en",.05,200.0) }
        activity=ActivityScenario.launch(MainActivity::class.java)
    }
    @After fun close(){if(::activity.isInitialized)activity.close()}
    @Test fun searchOpensDetailAndCharts() {
        compose.waitUntil(20000){compose.onAllNodesWithTag("commodity-search").fetchSemanticsNodes().isNotEmpty()}
        screenshot("home")
        compose.onNodeWithTag("commodity-search").performTextInput("tom")
        compose.onNodeWithTag("suggestion-tomato").performScrollTo().assertExists()
        screenshot("search")
        compose.onNodeWithTag("suggestion-tomato").performClick()
        compose.onNodeWithText("Price history").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("range-7").performScrollTo().performClick()
        compose.onNodeWithTag("range-90").performScrollTo().performClick().assertIsSelected()
        compose.onAllNodesWithTag("line-chart").onFirst().performScrollTo()
        screenshot("commodity-history")
        compose.onNodeWithTag("chart-tab-Arrivals").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("line-chart").performScrollTo()
        screenshot("arrivals")
        compose.onNodeWithTag("chart-tab-Estimate").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Backtest MAE:",substring=true).performScrollTo().assertExists()
        compose.onNodeWithTag("line-chart").performScrollTo()
        screenshot("forecast")
    }
    @Test fun pricesHaveRecordsAndDistrictBrowsing() {
        compose.waitUntil(20000){compose.onAllNodesWithTag("commodity-search").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Prices").performClick()
        compose.onNodeWithTag("price-search").performTextInput("Tomato")
        compose.onNodeWithText("1 matching observations").performScrollTo().assertExists()
        screenshot("prices")
        compose.onNodeWithText("Browse all 31 districts").performScrollTo().performClick()
        compose.onNodeWithText("Karnataka districts").assertExists()
        screenshot("districts")
        compose.onNodeWithText("Kolar").performScrollTo().performClick()
        screenshot("district")
        compose.onNodeWithText("TEST Market 1 (synthetic)").performClick()
        compose.onNodeWithText("Set as my mandi").assertExists()
        screenshot("mandi")
    }
}
