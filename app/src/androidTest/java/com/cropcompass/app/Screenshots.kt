package com.cropcompass.app

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assume

fun requireFixture() {
    val context=InstrumentationRegistry.getInstrumentation().targetContext
    val source=context.assets.open("data/metadata.json").bufferedReader().use{com.google.gson.JsonParser.parseReader(it).asJsonObject.get("dataSource").asString}
    Assume.assumeTrue("These UI acceptance scenarios require the labelled fixture bundle.",source=="fixture")
}

fun screenshot(name: String) {
    val instrumentation=InstrumentationRegistry.getInstrumentation()
    instrumentation.waitForIdleSync()
    instrumentation.uiAutomation.waitForIdle(150,3000)
    val directory=File(instrumentation.targetContext.externalMediaDirs.first(),"additional_test_output")
    directory.mkdirs()
    instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
        File(directory,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
}
