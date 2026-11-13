package dev.pleos.entry.boss

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pleos.boss.game.*
import dev.pleos.entry.boss.data.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GameStorageTest {
    private lateinit var db:GameDatabase
    private lateinit var scope:CoroutineScope
    private lateinit var repo:GameRepository
    private lateinit var balance:Balance
    private val ids=object:IdSource{override fun id()=UUID.randomUUID().toString();override fun seed()=24L}
    @Before fun create()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        balance=Balance.parse(context.assets.open("balance.json").bufferedReader().use{it.readText()})
        db=Room.inMemoryDatabaseBuilder(context,GameDatabase::class.java).build()
        scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        repo=GameRepository(db,scope,balance,ids)
        repo.ready.await()
    }
    @After fun close(){scope.cancel();db.close()}
    @Test fun concurrentChestRetriesPayOnlyOnce()=runBlocking {
        val encounter=repo.state.value.encounter!!
        val chest=RewardGenerator(balance).create(encounter,0,ids)
        repo.change{ActionResult(it.copy(chests=listOf(chest)),"",true)}
        coroutineScope {
            (1..10).map { async(Dispatchers.IO) {
                repo.change("same-open","open:"+chest.id){GameActions(balance).openChest(it,chest.id,1,UxMode.PARKED_INTERACTIVE)}
            }}.awaitAll()
        }
        assertEquals(chest.coins,repo.state.value.hero.coins)
        assertEquals(1,repo.state.value.equipment.size)
        assertEquals(1,repo.state.value.collection[chest.bossId])
        assertEquals(1,db.game().chests().size)
    }
    @Test fun upgradeReceiptSurvivesRepositoryRecreation()=runBlocking {
        val item=Equipment("twig_blade",Rarity.COMMON)
        repo.change{ActionResult(it.copy(hero=it.hero.copy(coins=100,dust=3),equipment=listOf(item)),"",true)}
        repo.change("upgrade-once","upgrade:"+item.key){GameActions(balance).upgrade(it,item.key,UxMode.PARKED_INTERACTIVE)}
        scope.cancel()
        scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        repo=GameRepository(db,scope,balance,ids)
        repo.ready.await()
        repo.change("upgrade-once","upgrade:"+item.key){GameActions(balance).upgrade(it,item.key,UxMode.PARKED_INTERACTIVE)}
        assertEquals(70,repo.state.value.hero.coins)
        assertEquals(2,repo.state.value.hero.dust)
        assertEquals(1,repo.state.value.equipment.single().upgradeLevel)
    }
    @Test fun sameActionIdWithDifferentPayloadIsRejected()=runBlocking {
        repo.change("id","first"){ActionResult(it.copy(hero=it.hero.copy(coins=40)),"first",true)}
        val result=repo.change("id","different"){ActionResult(it.copy(hero=it.hero.copy(coins=999)),"bad",true)}
        assertFalse(result.success);assertEquals(40,repo.state.value.hero.coins)
    }
    @Test fun databaseRejectsTwoChestsForOneEncounter()=runBlocking {
        val chest=RewardGenerator(balance).create(repo.state.value.encounter!!,0,ids)
        db.game().put(ChestRow(chest.id,chest.encounterId,Balance.json.encodeToString(Chest.serializer(),chest)))
        var rejected=false
        try{db.game().put(ChestRow("second-id",chest.encounterId,Balance.json.encodeToString(Chest.serializer(),chest)))}catch(_:android.database.sqlite.SQLiteConstraintException){rejected=true}
        assertTrue(rejected);assertEquals(1,db.game().chests().size)
    }
    @Test fun pendingDepositAndDeletedInventorySurviveRestart()=runBlocking {
        val item=Equipment("twig_blade",Rarity.RARE,3)
        repo.change{ActionResult(it.copy(equipment=listOf(item)),"",true)}
        repo.change("deposit-test","deposit"){EconomyActions(balance).reserve(it,item.key,"outgoing","test-realm",UxMode.PARKED_INTERACTIVE,1)}
        scope.cancel();scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        repo=GameRepository(db,scope,balance,ids);repo.ready.await()
        assertTrue(repo.state.value.equipment.isEmpty())
        assertEquals(item,repo.state.value.economy.deposits.single().item)
        repo.change{EconomyActions(balance).finishDeposit(it,"outgoing",true)}
        assertEquals(item,repo.state.value.equipment.single())
        assertTrue(repo.state.value.economy.deposits.isEmpty())
    }
    @Test fun repeatedDeliveryAfterRestartCannotCreditTwice()=runBlocking {
        val item=Equipment("twig_blade",Rarity.RARE,3)
        repo.change("delivery-test","fixed-item"){EconomyActions(balance).receive(it,item)}
        scope.cancel();scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        repo=GameRepository(db,scope,balance,ids);repo.ready.await()
        repo.change("delivery-test","fixed-item"){EconomyActions(balance).receive(it,item)}
        assertEquals(1,repo.state.value.equipment.size)
        assertEquals(0,repo.state.value.hero.dust)
    }
    @Test fun returnAndDistanceCheckpointCommitTogetherAcrossRestart()=runBlocking {
        val engine=IdleEngine(balance,ids)
        fun input(ms:Long,meters:Long)=ProgressInput(ProgressClock(1_800_000_000_000L+ms,ms,1),
            UxMode.PARKED_INTERACTIVE,true,DistanceSample(meters,ms,"TEST_ODOMETER_KM"))
        repo.change{ActionResult(engine.setEnabled(it,true,input(0,100_000)),"",true)}
        repo.change{ActionResult(engine.pause(it,1_800_000_000_000L),"",true)}
        scope.cancel();scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        repo=GameRepository(db,scope,balance,ids);repo.ready.await()
        coroutineScope {
            (1..8).map { async(Dispatchers.IO) {
                repo.change { ActionResult(engine.tick(it,input(60_000,102_000)).game,"",true) }
            }}.awaitAll()
        }
        assertEquals(80_000,repo.state.value.encounter!!.hpMilli)
        assertEquals(1,repo.state.value.events.count{it.type=="FINISHING_STRIKE"})
        assertEquals(102_000L,repo.state.value.hero.progress.distanceBaselineMeters)
        val settled=repo.state.value
        scope.cancel();scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        repo=GameRepository(db,scope,balance,ids);repo.ready.await()
        repo.change { ActionResult(engine.tick(it,input(60_000,102_000)).game,"",true) }
        assertEquals(settled,repo.state.value)
    }
}

@RunWith(AndroidJUnit4::class)
class FixtureJourneyUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private var saved:GameState?=null
    @Before fun saveProgress()=runBlocking {
        val graph=(compose.activity.application as BossApplication).graph
        graph.repository.ready.await();saved=graph.repository.state.value
        graph.repository.change{ActionResult(GameState(encounter=graph.balance.encounter(0,1,graph.ids)),"",true)}
        Unit
    }
    @After fun restoreProgress()=runBlocking {
        val graph=(compose.activity.application as BossApplication).graph
        saved?.let{original->graph.repository.change{ActionResult(original,"",true)}}
        Unit
    }
    @Test fun completeJourneyOpenLootAndVisitCollection(){
        Assume.assumeTrue(BuildConfig.IS_FIXTURE)
        val graph=(compose.activity.application as BossApplication).graph
        compose.waitUntil(10_000){graph.repository.ready.isCompleted}
        compose.onNodeWithTag("screen-home").assertExists()
        compose.onNodeWithTag("fixture-run").performScrollTo().performClick()
        compose.waitUntil(45_000){!graph.fixtureRunning.value&&graph.repository.state.value.unopened.isNotEmpty()}
        compose.onNodeWithTag("return-summary").performScrollTo()
        screenshot("01-home")
        compose.onNodeWithTag("nav-chests").performClick()
        compose.onNodeWithTag("screen-chests").assertExists()
        screenshot("02-chests")
        compose.onNodeWithTag("open-all").performClick()
        compose.waitUntil(10_000){graph.repository.state.value.unopened.isEmpty()&&graph.repository.state.value.equipment.isNotEmpty()}
        screenshot("03-rewards")
        compose.onNodeWithTag("nav-equipment").performClick()
        compose.onNodeWithTag("screen-equipment").assertExists()
        screenshot("04-equipment")
        compose.onNodeWithTag("nav-collection").performClick()
        compose.onNodeWithTag("screen-collection").assertExists()
        screenshot("05-collection")
        compose.onNodeWithTag("nav-diagnostics").performScrollTo().performClick()
        compose.onNodeWithTag("screen-diagnostics").assertExists()
        screenshot("06-diagnostics")
        assertEquals("FIXTURE",graph.gateway.state.value.source)
    }
    @Test fun drivingHidesCombatStopsLoopAndParkingAutomaticallySettles() {
        Assume.assumeTrue(BuildConfig.IS_FIXTURE)
        val graph=(compose.activity.application as BossApplication).graph
        val fixture=graph.gateway as dev.pleos.entry.boss.sdk.FixtureVehicleGateway
        compose.runOnUiThread{fixture.park()}
        graph.setEnabled(true)
        compose.waitUntil(10_000){graph.combatLoopActive.value&&graph.repository.state.value.hero.progress.distanceBaselineMeters!=null}
        compose.runOnUiThread{fixture.drive(android.os.SystemClock.elapsedRealtime())}
        compose.waitUntil(10_000){!graph.combatLoopActive.value&&graph.repository.state.value.hero.progress.paused}
        compose.onNodeWithTag("driving-limited").assertExists()
        compose.onNodeWithTag("battle-scene").assertDoesNotExist()
        val stopped=graph.repository.state.value
        Thread.sleep(1_200)
        assertEquals(stopped,graph.repository.state.value)
        compose.runOnUiThread{fixture.park(distanceMeters=(fixture.state.value.odometerMeters?:100_000)+2_000)}
        compose.waitUntil(10_000){graph.combatLoopActive.value&&
            (graph.repository.state.value.hero.progress.lastReturn?.strikeDamageMilli?:0)>0}
        compose.onNodeWithTag("return-summary").assertExists()
        val hp=graph.repository.state.value.encounter!!.hpMilli
        compose.waitUntil(5_000){graph.repository.state.value.encounter!!.hpMilli<hp}
    }
    @Test fun leavingAppStopsCombatUntilForegroundReturn() {
        Assume.assumeTrue(BuildConfig.IS_FIXTURE)
        val graph=(compose.activity.application as BossApplication).graph
        graph.setEnabled(true)
        compose.waitUntil(10_000){graph.combatLoopActive.value}
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.waitUntil(10_000){!graph.combatLoopActive.value&&graph.repository.state.value.hero.progress.paused}
        val stopped=graph.repository.state.value
        Thread.sleep(1_200)
        assertEquals(stopped,graph.repository.state.value)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.waitUntil(10_000){graph.combatLoopActive.value&&graph.repository.state.value.hero.progress.lastReturn!=null}
        assertTrue(graph.repository.state.value.encounter!!.hpMilli<stopped.encounter!!.hpMilli)
    }
    private fun screenshot(name:String){
        compose.waitForIdle()
        val file=File(compose.activity.filesDir,"screenshots/"+name+".png")
        file.parentFile!!.mkdirs()
        file.outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}
