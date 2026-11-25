package com.cropcompass.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.cropcompass.app.data.local.Preferences
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test

class PhaseOneTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityScenario<MainActivity>
    @Before fun openFreshGuestSession() {
        requireFixture()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { Preferences(context).guest(false); Preferences(context).region("kolar","test-1"); Preferences(context).configure("en",.05,200.0) }
        activity = ActivityScenario.launch(MainActivity::class.java)
    }
    @After fun close() { if(::activity.isInitialized)activity.close() }

    @Test fun guestCanExploreNavigateAndLogout() {
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Explore guest demo").fetchSemanticsNodes().isNotEmpty() }
        screenshot("login")
        compose.onNodeWithText("Explore guest demo").performScrollTo().performClick()
        // DataStore commits run on IO, outside Compose's animation/idling clock.
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Welcome to Crop Compass!").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Welcome to Crop Compass!").performScrollTo()
        compose.onNodeWithText("Welcome to Crop Compass!").assertIsDisplayed()
        compose.onAllNodesWithText("Tomato").onFirst().performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Guest demo").assertIsDisplayed()
        compose.onNodeWithText("Leave guest demo").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Explore guest demo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Explore guest demo").assertExists()
    }

    @Test fun registrationFormIsReachable() {
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Explore guest demo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("New here? Create an account").performScrollTo().performClick()
        compose.onNodeWithText("Full name").assertExists()
        compose.onNodeWithText("Confirm password").assertExists()
        compose.onNodeWithText("Create account").assertExists()
        screenshot("registration")
    }
}
