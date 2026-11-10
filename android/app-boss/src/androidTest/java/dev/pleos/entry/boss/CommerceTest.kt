package dev.pleos.entry.boss

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pleos.boss.game.*
import dev.pleos.entry.boss.social.SocialController
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CommerceUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun purchaseWithConfirmationAndOpenFriends() {
        Assume.assumeTrue(BuildConfig.IS_FIXTURE)
        val graph=(compose.activity.application as BossApplication).graph
        compose.waitUntil(10_000){graph.repository.ready.isCompleted}
        val original=graph.repository.state.value
        try {
            runBlocking { graph.repository.change{ActionResult(it.copy(hero=it.hero.copy(coins=300,dust=0)),"",true)} }
            compose.onNodeWithTag("nav-shop").performScrollTo().performClick()
            compose.onNodeWithTag("screen-shop").assertExists()
            compose.onNodeWithTag("buy-dust").performClick()
            compose.onNodeWithTag("confirm-action").performClick()
            compose.waitUntil(10_000){graph.repository.state.value.hero.coins==255&&graph.repository.state.value.hero.dust==5}
            screenshot("07-shop")
            compose.onNodeWithTag("nav-friends").performScrollTo().performClick()
            compose.onNodeWithTag("screen-friends").assertExists()
            screenshot("08-friends")
        } finally { runBlocking { graph.repository.change{ActionResult(original,"",true)} } }
    }
    private fun screenshot(name:String) {
        compose.waitForIdle()
        val file=File(compose.activity.filesDir,"screenshots/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}

@RunWith(AndroidJUnit4::class)
class SocialIntegrationTest {
    @Test fun twoIndependentUsersTradeAndDeliverExactlyOnce()=runBlocking {
        Assume.assumeTrue(BuildConfig.IS_FIXTURE)
        val context=ApplicationProvider.getApplicationContext<Context>()
        val graph=(context.applicationContext as BossApplication).graph
        graph.repository.ready.await()
        val original=graph.repository.state.value
        val prefsName="test-social-"+UUID.randomUUID()
        val social=SocialController(graph,prefsName)
        val otherToken=UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","")
        val endpoint="http://10.0.2.2:8765"
        val localItem=Equipment("twig_blade",Rarity.RARE,3)
        val otherItem=Equipment("leaf_coat",Rarity.EPIC,2)
        suspend fun done() {
            withTimeout(30_000){while(social.busy.value)delay(40)}
            assertNull(social.state.value.error)
        }
        fun other(path:String,body:JSONObject?=null):JSONObject {
            val connection=URL(endpoint+path).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout=10000;connection.readTimeout=10000
                connection.setRequestProperty("Authorization","Bearer $otherToken")
                if(body!=null){connection.requestMethod="POST";connection.doOutput=true;connection.setRequestProperty("Content-Type","application/json");connection.outputStream.use{it.write(body.toString().toByteArray())}}
                check(connection.responseCode in 200..299){"Peer HTTP status ${connection.responseCode}"}
                return JSONObject(connection.inputStream.bufferedReader().use{it.readText()})
            } finally {connection.disconnect()}
        }
        try {
            graph.repository.change{ActionResult(it.copy(equipment=listOf(localItem),equipped=emptyMap(),economy=EconomyState()),"",true)}
            social.connect(endpoint,"시험 원정가 A");done()
            val peer=other("/v1/profile",JSONObject().put("nickname","시험 원정가 B"))
            other("/v1/friends/request",JSONObject().put("code",social.state.value.profile!!.code))
            social.refresh();done()
            social.friendRequest(social.state.value.snapshot!!.requests.single().id,"accept");done()
            assertEquals(1,social.state.value.snapshot!!.friends.size)
            social.deposit(localItem.key);done()
            assertTrue(graph.repository.state.value.equipment.isEmpty())
            assertTrue(graph.repository.state.value.economy.deposits.isEmpty())
            val otherId=UUID.randomUUID().toString()
            other("/v1/deposits",JSONObject().put("id",otherId).put("item",JSONObject(Balance.json.encodeToString(Equipment.serializer(),otherItem))))
            social.refresh();done()
            val offeredId=social.state.value.snapshot!!.inventory.single().id
            social.offer(peer.getString("id"),offeredId,otherId);done()
            val tid=other("/v1/state").getJSONArray("trades").getJSONObject(0).getString("id")
            other("/v1/trades/$tid/accept",JSONObject())
            social.refresh();done()
            assertEquals(otherItem,social.state.value.snapshot!!.inventory.single().item)
            social.withdraw(otherId);done()
            assertEquals(listOf(otherItem),graph.repository.state.value.equipment)
            val dust=graph.repository.state.value.hero.dust
            social.withdraw(otherId);done()
            assertEquals(dust,graph.repository.state.value.hero.dust)
            assertEquals(listOf(otherItem),graph.repository.state.value.equipment)
            val peerInventory=other("/v1/state").getJSONArray("inventory")
            assertEquals(offeredId,peerInventory.getJSONObject(0).getString("id"))
            assertEquals(3,peerInventory.getJSONObject(0).getJSONObject("item").getInt("upgradeLevel"))
        } finally {
            graph.repository.change{ActionResult(original,"",true)}
            context.getSharedPreferences(prefsName,Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
