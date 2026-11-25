package com.cropcompass.app

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.cropcompass.app.data.local.Preferences
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.io.FileInputStream

class LocaleOfflineTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var activity:ActivityScenario<MainActivity>
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun shell(command:String):String=instrumentation.uiAutomation.executeShellCommand(command).use{FileInputStream(it.fileDescriptor).bufferedReader().use{reader->reader.readText()}}
    @Before fun setup(){
        requireFixture()
        instrumentation.runOnMainSync{AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))}
        runBlocking{Preferences(context).guest(true);Preferences(context).region("kolar","test-1");Preferences(context).configure("en",.05,200.0)}
        activity=ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(20000){compose.onAllNodesWithTag("commodity-search").fetchSemanticsNodes().isNotEmpty()}
    }
    @After fun close(){
        if(!::activity.isInitialized)return
        instrumentation.runOnMainSync{AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))}
        runBlocking{Preferences(context).configure("en",.05,200.0)}
        if(::activity.isInitialized)activity.close()
    }
    @Test fun kannadaSwitchRecreatesAndChangesNavigation(){
        compose.onNodeWithTag("nav-profile").performClick()
        compose.onNodeWithTag("language-kn").performScrollTo().performClick()
        compose.waitUntil(20000){compose.onAllNodesWithText("ಮುಖಪುಟ").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("nav-home").performClick()
        compose.onNodeWithTag("commodity-search").assertExists()
        screenshot("kannada")
        compose.onNodeWithTag("nav-profile").performClick()
        compose.onNodeWithTag("language-en").performScrollTo().performClick()
        compose.waitUntil(20000){compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun cachedBrowsingWorksWithNetworkDisabled(){
        val wifi=shell("settings get global wifi_on").trim()=="1"
        val mobile=shell("settings get global mobile_data").trim()=="1"
        val airplane=shell("settings get global airplane_mode_on").trim()=="1"
        try{
            shell("cmd connectivity airplane-mode enable");shell("svc wifi disable");shell("svc data disable")
            val connectivity=context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            compose.waitUntil(20000){connectivity.activeNetwork==null}
            activity.recreate()
            compose.waitUntil(20000){compose.onAllNodesWithText("Offline • using observations cached on this device. They may be outdated.").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("nav-prices").performClick()
            compose.onNodeWithText("5 matching observations").performScrollTo().assertExists()
            screenshot("offline-prices")
        }finally{shell("cmd connectivity airplane-mode ${if(airplane) "enable" else "disable"}");shell("svc wifi ${if(wifi) "enable" else "disable"}");shell("svc data ${if(mobile) "enable" else "disable"}")}
    }
    @Test fun aboutAndMissingInsightsAreHonest(){
        compose.onNodeWithTag("nav-profile").performClick()
        compose.onNodeWithText("About the data").performScrollTo().performClick()
        compose.onNodeWithText("Records:",substring=true).assertExists();screenshot("about-data")
        compose.onNodeWithTag("nav-profile").performClick()
        compose.onNodeWithText("Market insights").performScrollTo().performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText("No Spark insight files have been supplied.",substring=true).fetchSemanticsNodes().isNotEmpty()}
        screenshot("insights")
    }
}
