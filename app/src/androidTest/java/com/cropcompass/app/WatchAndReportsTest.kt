package com.cropcompass.app

import android.app.Activity
import android.app.Instrumentation
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.platform.app.InstrumentationRegistry
import com.cropcompass.app.data.Alerts
import com.cropcompass.app.data.local.Preferences
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File

class WatchAndReportsTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var activity:ActivityScenario<MainActivity>
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val container get()=(context.applicationContext as CropCompassApplication).container
    @Before fun setup(){
        requireFixture()
        if(Build.VERSION.SDK_INT>=33)instrumentation.uiAutomation.grantRuntimePermission(context.packageName,android.Manifest.permission.POST_NOTIFICATIONS)
        runBlocking {
            Preferences(context).guest(true);Preferences(context).region("kolar","test-1");Preferences(context).configure("en",.05,200.0)
            container.market.dao.watchlist().first().filter{it.ownerUid=="guest"}.forEach{container.market.dao.deleteWatch(it.id)}
            container.market.dao.reports().first().filter{it.ownerUid=="guest"}.forEach{container.market.dao.deleteReport(it.id)}
        }
        Intents.init();activity=ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(20000){compose.onAllNodesWithTag("commodity-search").fetchSemanticsNodes().isNotEmpty()}
    }
    @After fun close(){if(::activity.isInitialized){activity.close();Intents.release()}}
    private fun dismissFeedback(message: String) {
        compose.waitUntil(10000){compose.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Dismiss").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText(message).fetchSemanticsNodes().isEmpty()}
    }
    @Test fun watchCrudAndNotification(){
        compose.onNodeWithTag("commodity-search").performTextInput("Tomato");Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("suggestion-tomato").performClick()
        compose.onNodeWithTag("add-watch").performScrollTo().performClick()
        compose.onNodeWithTag("watch-target").performTextInput("1");Espresso.closeSoftKeyboard()
        screenshot("watchlist-form")
        compose.onNodeWithTag("save-watch").performClick()
        compose.waitUntil(10000){runBlocking{container.market.dao.watchlist().first().isNotEmpty()}}
        compose.onNodeWithTag("simulate-alert").performScrollTo().performClick()
        val notifications=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        compose.waitUntil(10000){notifications.activeNotifications.any{it.notification.channelId==Alerts.CHANNEL}}
        assertTrue(notifications.activeNotifications.any{it.notification.extras.getCharSequence("android.title").toString().contains("TEST DATA")})
        dismissFeedback("1 alert notification posted.")
        screenshot("watchlist-alert")
        compose.onNodeWithTag("edit-watch").performScrollTo().performClick()
        compose.waitUntil(10000){compose.onAllNodesWithTag("watch-target").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("watch-target").performTextClearance();compose.onNodeWithTag("watch-target").performTextInput("1200");Espresso.closeSoftKeyboard();compose.onNodeWithTag("save-watch").performClick()
        compose.waitUntil(10000){runBlocking{container.market.dao.watchlist().first().single().targetPrice==1200.0}}
        compose.onNodeWithTag("watch-enable").performScrollTo().performClick()
        compose.waitUntil(10000){runBlocking{!container.market.dao.watchlist().first().single().enabled}}
        compose.onNodeWithTag("delete-watch").performScrollTo().performClick()
        compose.waitUntil(10000){runBlocking{container.market.dao.watchlist().first().isEmpty()}}
    }
    @Test fun photoReportPersistsAndDoesNotChangeOfficialPrices(){
        val before=runBlocking{container.market.dao.countPrices()}
        val directory=File(context.cacheDir,"camera").apply{mkdirs()};val file=File(directory,"instrumented-photo.png")
        val bitmap=Bitmap.createBitmap(300,200,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);canvas.drawColor(Color.GREEN);canvas.drawText("TEST PHOTO",25f,100f,Paint().apply{color=Color.BLACK;textSize=28f});file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.photos",file)
        Intents.intending(IntentMatchers.hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK,Intent().setData(uri)))
        compose.onNodeWithText("Report a price").performScrollTo().performClick()
        compose.onNodeWithTag("report-price").performTextInput("1234")
        compose.onNodeWithTag("report-note").performScrollTo().performTextInput("Instrumented photo report");Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("gallery-photo").performScrollTo().performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText("Photo attached").fetchSemanticsNodes().isNotEmpty()}
        screenshot("report-form")
        compose.onNodeWithTag("save-report").performClick()
        compose.waitUntil(10000){runBlocking{container.market.dao.reports().first().any{it.note=="Instrumented photo report"}}}
        val report=runBlocking{container.market.dao.reports().first().first{it.note=="Instrumented photo report"}}
        assertTrue(File(android.net.Uri.parse(report.photoUri).path!!).exists());assertFalse(report.synced);assertEquals(before,runBlocking{container.market.dao.countPrices()})
        dismissFeedback("Saved on device. Sign in to sync.")
        compose.onNodeWithText("Instrumented photo report").performScrollTo();screenshot("reports")
        compose.onNodeWithText("Edit").performScrollTo().performClick()
        compose.waitUntil(10000){compose.onAllNodesWithTag("report-price").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("report-price").performTextClearance();compose.onNodeWithTag("report-price").performTextInput("1300");Espresso.closeSoftKeyboard();compose.onNodeWithTag("save-report").performClick()
        compose.waitUntil(10000){runBlocking{container.market.dao.reports().first().first().price==1300.0}}
        dismissFeedback("Saved on device. Sign in to sync.")
        compose.onNodeWithText("Delete").performScrollTo().performClick();compose.onNodeWithTag("confirm-delete-report").performClick()
        compose.waitUntil(10000){runBlocking{container.market.dao.reports().first().isEmpty()}}
        compose.waitUntil(10000){!File(android.net.Uri.parse(report.photoUri).path!!).exists()}
    }
}
