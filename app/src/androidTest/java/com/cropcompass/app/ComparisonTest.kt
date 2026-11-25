package com.cropcompass.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.cropcompass.app.data.local.Preferences
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import android.view.MotionEvent
import android.os.SystemClock
import android.graphics.PointF

class ComparisonTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var activity: ActivityScenario<MainActivity>
    @Before fun setup(){requireFixture();val context=InstrumentationRegistry.getInstrumentation().targetContext;runBlocking{Preferences(context).guest(true);Preferences(context).region("kolar","test-1");Preferences(context).configure("en",.05,99999.0)};activity=ActivityScenario.launch(MainActivity::class.java)}
    @After fun close(){if(::activity.isInitialized)activity.close()}
    @Test fun computesRankedResultsAndMapSheet(){
        compose.waitUntil(20000){compose.onAllNodesWithTag("nav-compare").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("nav-compare").performClick()
        compose.onNodeWithTag("run-compare").performScrollTo().performClick()
        compose.onNodeWithTag("rank-1").performScrollTo().assertExists()
        screenshot("comparison")
        compose.onNodeWithTag("nav-prices").performClick()
        compose.onNodeWithText("Map").performScrollTo().performClick()
        compose.onNodeWithTag("map-view").performScrollTo().assertExists()
        screenshot("map")
        compose.onNodeWithTag("map-marker-test-1").performScrollTo().performClick()
        compose.onNodeWithText("Approximate location — district-level coordinate.").assertExists()
        screenshot("map-marker")
    }
    @Test fun geographicMarkerTapOpensDetails(){
        compose.waitUntil(20000){compose.onAllNodesWithTag("nav-prices").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("nav-prices").performClick()
        compose.onNodeWithText("Map").performScrollTo().performClick()
        compose.onNodeWithTag("map-view").performScrollTo()
        val point=PointF()
        activity.onActivity { screen->
            val map=screen.window.decorView.findViewWithTag<MapView>("mandi-map")
            val marker=map.overlays.filterIsInstance<Marker>().single()
            val pixels=map.projection.toPixels(marker.position,null);val offset=IntArray(2);map.getLocationOnScreen(offset)
            point.set((offset[0]+pixels.x).toFloat(),(offset[1]+pixels.y).toFloat())
        }
        val time=SystemClock.uptimeMillis();val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,point.x,point.y,0).also{automation.injectInputEvent(it,true);it.recycle()}
        MotionEvent.obtain(time,time+80,MotionEvent.ACTION_UP,point.x,point.y,0).also{automation.injectInputEvent(it,true);it.recycle()}
        compose.waitUntil(10000){compose.onAllNodesWithText("Approximate location — district-level coordinate.").fetchSemanticsNodes().isNotEmpty()}
        screenshot("map-native-tap")
    }
}
