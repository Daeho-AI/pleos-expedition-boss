package dev.pleos.boss.game

import kotlin.test.*
import org.junit.Test

class EconomyTest {
    private val balance = Balance.parse(javaClass.getResource("/balance.json")!!.readText())
    private val economy = EconomyActions(balance)
    private val parked = UxMode.PARKED_INTERACTIVE
    private val item = Equipment("twig_blade",Rarity.COMMON,3)
    private fun funded() = GameState(hero=Hero(level=3,coins=1000,dust=0))
    @Test fun buyingDebitsExactlyAndGrantsFixedContents() {
        val result=economy.buy(funded(),"dust",parked,1)
        assertTrue(result.success);assertEquals(955,result.game.hero.coins);assertEquals(5,result.game.hero.dust)
        assertEquals(-45,result.game.economy.history.single().coins)
    }
    @Test fun insufficientCoinsAndUnknownProductDoNotMutate() {
        val game=GameState()
        assertEquals(game,economy.buy(game,"dust",parked,1).game)
        assertFalse(economy.buy(funded(),"anything-free",parked,1).success)
    }
    @Test fun ownedGearAndLevelGateRejectPurchase() {
        val product="gear:twig_blade:RARE"
        assertFalse(economy.buy(funded().copy(hero=Hero(coins=1000)),product,parked,1).success)
        val first=economy.buy(funded(),product,parked,1)
        assertTrue(first.success)
        val second=economy.buy(first.game,product,parked,2)
        assertFalse(second.success);assertEquals(first.game,second.game)
    }
    @Test fun fragmentShopStopsAfterCollectionUnlock() {
        val game=funded().copy(collection=mapOf("moss_guard" to 9))
        val first=economy.buy(game,"fragment:moss_guard",parked,1)
        assertEquals(10,first.game.collection["moss_guard"])
        assertFalse(economy.buy(first.game,"fragment:moss_guard",parked,2).success)
    }
    @Test fun drivingCannotBuySellOrReserve() {
        val game=funded().copy(equipment=listOf(item))
        for(ux in listOf(UxMode.DRIVING_LIMITED,UxMode.UNKNOWN)) {
            assertEquals(game,economy.buy(game,"dust",ux,1).game)
            assertEquals(game,economy.sell(game,item.key,ux,1).game)
            assertEquals(game,economy.reserve(game,item.key,"deposit","realm",ux,1).game)
        }
    }
    @Test fun equippedItemCannotBeSoldOrTransferred() {
        val game=funded().copy(equipment=listOf(item),equipped=mapOf(Slot.WEAPON to item.key))
        assertFalse(economy.sell(game,item.key,parked,1).success)
        assertFalse(economy.reserve(game,item.key,"deposit","realm",parked,1).success)
    }
    @Test fun saleRemovesItemSoRepeatedSaleDoesNotPay() {
        val first=economy.sell(funded().copy(equipment=listOf(item)),item.key,parked,1)
        assertEquals(1039,first.game.hero.coins);assertTrue(first.game.equipment.isEmpty())
        assertEquals(first.game,economy.sell(first.game,item.key,parked,2).game)
    }
    @Test fun reservationPreventsSaleUpgradeOrSecondDeposit() {
        val first=economy.reserve(funded().copy(equipment=listOf(item)),item.key,"deposit","realm",parked,1)
        assertTrue(first.success);assertTrue(first.game.equipment.isEmpty())
        assertEquals(item,first.game.economy.deposits.single().item)
        assertFalse(economy.sell(first.game,item.key,parked,2).success)
        assertFalse(GameActions(balance).upgrade(first.game,item.key,parked).success)
        assertFalse(economy.reserve(first.game,item.key,"other","realm",parked,2).success)
    }
    @Test fun cancelledDepositRefundsOnlyOnce() {
        val reserved=economy.reserve(funded().copy(equipment=listOf(item)),item.key,"deposit","realm",parked,1).game
        val refunded=economy.finishDeposit(reserved,"deposit",true).game
        assertEquals(listOf(item),refunded.equipment)
        assertTrue(refunded.economy.deposits.isEmpty())
        assertEquals(refunded,economy.finishDeposit(refunded,"deposit",true).game)
    }
    @Test fun committedDepositDoesNotKeepLocalCopy() {
        val reserved=economy.reserve(funded().copy(equipment=listOf(item)),item.key,"deposit","realm",parked,1).game
        val committed=economy.finishDeposit(reserved,"deposit",false).game
        assertTrue(committed.equipment.isEmpty());assertTrue(committed.economy.deposits.isEmpty())
    }
    @Test fun receivingDuplicatePreservesHighestUpgrade() {
        val existing=funded().copy(equipment=listOf(item))
        val merged=economy.receive(existing,item.copy(upgradeLevel=6)).game
        assertEquals(6,merged.equipment.single().upgradeLevel)
        assertEquals(1,merged.hero.dust)
        assertFalse(economy.receive(existing,item.copy(upgradeLevel=11)).success)
    }
    @Test fun oldSaveWithoutEconomyDefaultsSafely() {
        val old="""{"hero":{"coins":123}}"""
        val loaded=Balance.json.decodeFromString<GameState>(old)
        assertEquals(123,loaded.hero.coins);assertEquals(EconomyState(),loaded.economy)
    }
}
