package dev.pleos.entry.boss

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.runner.RunWith
import java.io.File

/** Explicitly requested PC-server recording; no new friendship or trade is sent. */
@RunWith(AndroidJUnit4::class)
class SocialRecordingTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun showExistingExchange() {
        Assume.assumeTrue(BuildConfig.IS_FIXTURE && InstrumentationRegistry.getArguments().getString("recordSocial")=="true")
        val graph=(compose.activity.application as BossApplication).graph
        compose.onNodeWithTag("nav-friends").performClick()
        compose.waitUntil(20_000){!graph.social.busy.value}
        graph.social.refresh()
        compose.waitUntil(20_000){!graph.social.busy.value && graph.social.state.value.snapshot!=null}
        Assert.assertNull(graph.social.state.value.error)
        Assert.assertTrue(graph.social.state.value.snapshot!!.friends.isNotEmpty())
        Assert.assertTrue(graph.social.state.value.snapshot!!.trades.any{it.status=="ACCEPTED"})
        compose.onNodeWithText("교환",substring=false).performClick()
        compose.onNodeWithText("교환 내역").performScrollTo()
        compose.onAllNodesWithText("교환 완료").onFirst().performScrollTo()
        compose.waitForIdle()
        val folder=File(compose.activity.filesDir,"demo-capture").apply{mkdirs()}
        File(folder,"social-start.txt").writeText(SystemClock.elapsedRealtime().toString())
        File(folder,"friends.png").outputStream().use{InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}
        Thread.sleep(12_000)
    }
}
