package dev.pleos.boss.game

import kotlin.test.*

class AutoCombatTest {
    private val balance = Balance.parse(javaClass.getResource("/balance.json")!!.readText())
    private fun game() = GameState(hero = Hero(autoExpeditionEnabled = true))

    @Test fun baseCombatNeedsNoDrivingOrNavigationData() {
        val result = AutoCombat(balance, TestIds()).advance(game(), 60_000, 100_000)
        assertEquals(120_000, result.encounter!!.hpMilli)
        assertEquals(60_000, result.activeSession!!.eligibleMs)
    }

    @Test fun characterGrowthIncreasesDamageWithoutVehicleSpeed() {
        val normal = AutoCombat(balance, TestIds()).advance(game(), 30_000, 100_000)
        val boosted = AutoCombat(balance, TestIds()).advance(game().copy(hero = Hero(level = 6, autoExpeditionEnabled = true)), 30_000, 100_000)
        assertEquals(150_000, normal.encounter!!.hpMilli)
        assertEquals(120_000, boosted.encounter!!.hpMilli)
    }

    @Test fun continuousHuntAdvancesPastTwoBosses() {
        val result = AutoCombat(balance, TestIds()).advance(game(), 1_200_000, 2_000_000)
        assertTrue(result.chests.size > 2)
        assertEquals(result.chests.size, result.chests.map { it.encounterId }.distinct().size)
        assertEquals(result.chests.size, result.activeSession!!.kills)
    }

    @Test fun batchingAndRepeatedTicksGiveTheSameGrowthAndLoot() {
        val batch = AutoCombat(balance, TestIds()).advance(game(), 900_000, 900_000)
        val engine = AutoCombat(balance, TestIds())
        var ticks = game()
        repeat(900) { ticks = engine.advance(ticks, 1_000, (it + 1) * 1_000L) }
        assertEquals(batch.hero, ticks.hero)
        assertEquals(batch.encounter, ticks.encounter)
        assertEquals(batch.chests, ticks.chests)
        assertEquals(batch.activeSession, ticks.activeSession)
    }

    @Test fun fractionalDamageSurvivesSmallTicksAndSerialization() {
        val item = Equipment("moss_charm", Rarity.COMMON)
        val start = game().copy(equipment = listOf(item), equipped = mapOf(Slot.CHARM to item.key))
        val engine = AutoCombat(balance, TestIds())
        var result = engine.advance(start, 1, 1)
        result = Balance.json.decodeFromString(GameState.serializer(), Balance.json.encodeToString(GameState.serializer(), result))
        repeat(9) { result = engine.advance(result, 1, it + 2L) }
        assertEquals(179_989, result.encounter!!.hpMilli)
        assertEquals(0, result.hero.combatRemainder)
    }

    @Test fun disabledOrZeroTimeDoesNotCreateLootOrAnEncounter() {
        val engine = AutoCombat(balance, TestIds())
        assertEquals(GameState(), engine.advance(GameState(), 60_000, 100_000))
        assertEquals(game(), engine.advance(game(), 0, 100_000))
    }

    @Test fun anOldHeroSaveDefaultsTheDamageRemainder() {
        val hero = Balance.json.decodeFromString<Hero>("""{"coins":42,"autoExpeditionEnabled":true}""")
        assertEquals(42, hero.coins)
        assertEquals(0, hero.combatRemainder)
    }
}
