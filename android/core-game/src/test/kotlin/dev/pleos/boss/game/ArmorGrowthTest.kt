package dev.pleos.boss.game

import kotlin.test.*

class ArmorGrowthTest {
    private val b=Balance.parse(javaClass.getResource("/balance.json")!!.readText())

    @Test fun equippingAndUpgradingArmorReducesBossHpFasterWithoutResettingGear(){
        val item=Equipment("iron_plate",Rarity.RARE,2)
        val ids=TestIds()
        val base=GameState(hero=Hero(coins=500,dust=20,autoExpeditionEnabled=true),
            encounter=b.encounter(0,1,ids),equipment=listOf(item))
        val equipped=GameActions(b).equip(base,item.key,UxMode.PARKED_INTERACTIVE).game
        val upgrade=GameActions(b).upgrade(equipped,item.key,UxMode.PARKED_INTERACTIVE)
        assertTrue(upgrade.success)
        assertEquals(3,upgrade.game.equipment.single().upgradeLevel)
        assertEquals(item.key,upgrade.game.equipped[Slot.ARMOR])
        assertEquals(500-3*b.upgrades.coinMultiplier,upgrade.game.hero.coins)
        assertEquals(20-3*b.upgrades.dustMultiplier,upgrade.game.hero.dust)
        val combat=AutoCombat(b,ids)
        val bareHp=combat.advance(base,1000,1000).encounter!!.hpMilli
        val wornHp=combat.advance(equipped,1000,1000).encounter!!.hpMilli
        val upgradedHp=combat.advance(upgrade.game,1000,1000).encounter!!.hpMilli
        assertTrue(wornHp<bareHp,"An equipped armor must improve real combat")
        assertTrue(upgradedHp<wornHp,"Spending materials on armor must improve real combat")
        assertEquals(base.encounter!!.id,upgrade.game.encounter!!.id)
    }

    @Test fun armorCannotBeUpgradedWhileDriving(){
        val item=Equipment("leaf_coat",Rarity.COMMON)
        val game=GameState(hero=Hero(coins=500,dust=20),equipment=listOf(item),equipped=mapOf(Slot.ARMOR to item.key))
        val result=GameActions(b).upgrade(game,item.key,UxMode.DRIVING_LIMITED)
        assertFalse(result.success)
        assertEquals(game,result.game)
    }
}
