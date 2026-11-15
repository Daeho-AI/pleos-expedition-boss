package dev.pleos.entry.boss

import android.car.Car
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dev.pleos.boss.game.UxMode
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real SDK event updates must not reset a parked game's screen. */
@RunWith(AndroidJUnit4::class)
class PleosParkedUiTest {
    @get:Rule(order=0) val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Car.PERMISSION_SPEED, Car.PERMISSION_POWERTRAIN)
    @get:Rule(order=1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun freshSdkEventsKeepParkedBattleVisibleAndProgressing() {
        assumeTrue(!BuildConfig.IS_FIXTURE && Build.FINGERPRINT.startsWith("Pleos/crp_avd/"))
        val graph=(compose.activity.application as BossApplication).graph
        compose.waitUntil(30_000) {
            graph.repository.ready.isCompleted && graph.gateway.state.value.input().ux==UxMode.PARKED_INTERACTIVE
        }
        val wasEnabled=graph.repository.state.value.hero.autoExpeditionEnabled
        try {
            compose.onNodeWithTag("enter-battle").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("battle-scene").fetchSemanticsNodes().isNotEmpty() }
            val before=graph.repository.state.value
            val oldSdkTimestamp=graph.gateway.state.value.uxAtMs!!
            repeat(18) {
                Thread.sleep(350)
                compose.onNodeWithTag("screen-battle").assertExists()
                compose.onNodeWithText("첫 원정을 준비해요").assertDoesNotExist()
                compose.onNodeWithTag("battle-arrival").assertDoesNotExist()
            }
            val after=graph.repository.state.value
            assertTrue("Live SDK updates were not observed",graph.gateway.state.value.uxAtMs!!>oldSdkTimestamp)
            assertTrue("Parked combat did not advance",after.encounter?.id!=before.encounter?.id ||
                after.encounter!!.hpMilli<before.encounter!!.hpMilli)
            assertTrue(graph.combatLoopActive.value)
        } finally {
            if(!wasEnabled) graph.setEnabled(false)
        }
    }
}
