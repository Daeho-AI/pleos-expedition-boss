package dev.pleos.entry.boss

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pleos.boss.game.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import java.io.File

/** Opt-in recording scenario; never changes the user's saved game permanently. */
@RunWith(AndroidJUnit4::class)
class DemoRecordingTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun recordGameFeatures() {
        Assume.assumeTrue(BuildConfig.IS_FIXTURE && InstrumentationRegistry.getArguments().getString("recordDemo")=="true")
        val graph=(compose.activity.application as BossApplication).graph
        runBlocking{graph.repository.ready.await()}
        val original=graph.repository.state.value
        val folder=File(compose.activity.filesDir,"demo-capture").apply{mkdirs()}
        val timeline=File(folder,"timeline.tsv");timeline.writeText("")
        fun mark(name:String,seconds:Long){
            compose.waitForIdle()
            timeline.appendText(name+"\t"+SystemClock.elapsedRealtime()+"\n")
            File(folder,name+".png").outputStream().use{InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}
            // Compose's test clock must keep advancing while the native recorder
            // runs; a plain sleep would freeze visual transitions in the footage.
            val until=SystemClock.elapsedRealtime()+seconds*1000
            var previous=SystemClock.elapsedRealtime()
            while(SystemClock.elapsedRealtime()<until){
                Thread.sleep(16)
                val now=SystemClock.elapsedRealtime()
                compose.mainClock.advanceTimeBy((now-previous).coerceAtLeast(1))
                previous=now
            }
        }
        val sword=Equipment("twig_blade",Rarity.RARE,1)
        val armor=Equipment("leaf_coat",Rarity.RARE,1)
        val staff=Equipment("night_staff",Rarity.EPIC,2)
        try {
            runBlocking{graph.repository.change{ActionResult(GameState(
                hero=Hero(level=5,coins=2400,dust=50),encounter=graph.balance.encounter(0,1,graph.ids),
                equipment=listOf(sword,armor,staff),equipped=mapOf(Slot.WEAPON to sword.key,Slot.ARMOR to armor.key)),"",true)}}
            compose.onNodeWithTag("screen-home").assertExists()
            compose.onNodeWithText("시연").assertDoesNotExist()
            compose.onNodeWithTag("fixture-run").assertDoesNotExist()
            mark("home",10)
            compose.onNodeWithTag("enter-battle").performScrollTo().performClick()
            compose.waitUntil(10_000){compose.onAllNodesWithTag("battle-scene").fetchSemanticsNodes().isNotEmpty()}
            mark("battle",13)
            compose.onNodeWithTag("exit-battle").performClick()
            graph.simulateTrip(420,10.0)
            compose.waitUntil(30_000){!graph.fixtureRunning.value&&graph.repository.state.value.hero.progress.lastReturn!=null}
            compose.onNodeWithTag("return-summary").performScrollTo().performClick()
            mark("return-receipt",9)
            compose.onNodeWithText("확인").performClick()
            compose.onNodeWithTag("enter-battle").performScrollTo().performClick()
            mark("strike-entry",10)
            compose.onNodeWithTag("exit-battle").performClick()
            compose.onNodeWithTag("nav-chests").performClick()
            mark("chests",4)
            val chest=graph.repository.state.value.unopened.last()
            compose.onNodeWithTag("open-chest-"+chest.id).performClick()
            compose.waitUntil(10_000){graph.repository.state.value.chests.first{it.id==chest.id}.openedAt!=null}
            mark("loot",7)
            compose.onNodeWithText("확인").performClick()
            compose.onNodeWithTag("nav-equipment").performClick()
            compose.onNodeWithTag("gear-"+staff.key).performClick()
            mark("gear-preview",4)
            compose.onNodeWithTag("equip-selected").performScrollTo().performClick()
            compose.waitUntil(10_000){graph.repository.state.value.equipped[Slot.WEAPON]==staff.key}
            mark("gear-equipped",3)
            compose.onNodeWithTag("upgrade-selected").performScrollTo().performClick()
            compose.waitUntil(10_000){graph.repository.state.value.equipment.first{it.key==staff.key}.upgradeLevel==3}
            mark("gear-upgraded",6)
            compose.onNodeWithTag("nav-shop").performClick();mark("shop",7)
            compose.onNodeWithTag("nav-friends").performClick();mark("friends",9)
            compose.onNodeWithTag("nav-home").performClick();mark("closing",9)
        }finally{
            runBlocking{graph.repository.change{ActionResult(original,"",true)}}
        }
    }
}
