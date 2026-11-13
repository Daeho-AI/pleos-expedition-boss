package dev.pleos.boss.game

import kotlin.test.*

class TestIds : IdSource {
    private var sequence = 0L
    override fun id(): String = "test-" + (++sequence)
    override fun seed(): Long = ++sequence * 761
}
class GameTest {
    private val b = Balance.parse(javaClass.getResource("/balance.json")!!.readText())
    private fun rewarded() = AutoCombat(b, TestIds()).advance(GameState(hero = Hero(autoExpeditionEnabled = true)), 180_000, 180_000)
    @Test fun chestIsGrantedExactlyOnceAndDuplicatesBecomeDust() {
        val game = rewarded()
        val actions = GameActions(b)
        val chest = game.chests.single()
        val opened = actions.openChest(game, chest.id, 1, UxMode.PARKED_INTERACTIVE)
        assertEquals(opened.game, actions.openChest(opened.game, chest.id, 2, UxMode.PARKED_INTERACTIVE).game)
        val duplicate = chest.copy(id = "another", encounterId = "another-encounter", openedAt = null)
        val result = actions.openChest(opened.game.copy(chests = opened.game.chests + duplicate), duplicate.id, 3, UxMode.PARKED_INTERACTIVE)
        assertEquals(1, result.game.equipment.size)
        assertEquals(b.chest.duplicateDust[chest.rarity], result.game.hero.dust)
        assertEquals(2, result.game.collection[chest.bossId])
    }
    @Test fun pityUsesChestGenerationNotOpeningOrder() {
        val generator = RewardGenerator(b)
        val ids = TestIds()
        val encounter = b.encounter(0, 1, ids)
        repeat(500) { assertTrue(generator.create(encounter, 9, ids).rarity != Rarity.COMMON) }
    }
    @Test fun nonParkedMutationsFailWithoutChangingState() {
        val game = rewarded()
        for (ux in listOf(UxMode.UNKNOWN, UxMode.DRIVING_LIMITED)) {
            val result = GameActions(b).openChest(game, game.chests.single().id, 0, ux)
            assertFalse(result.success)
            assertEquals(game, result.game)
        }
    }
    @Test fun upgradeCostsAndGearStatsFollowBalance() {
        val item = Equipment("sand_hammer", Rarity.EPIC, 1)
        val game = GameState(hero = Hero(coins = 100, dust = 3), equipment = listOf(item), equipped = mapOf(Slot.WEAPON to item.key))
        val result = GameActions(b).upgrade(game, item.key, UxMode.PARKED_INTERACTIVE)
        assertEquals(40, result.game.hero.coins)
        assertEquals(1, result.game.hero.dust)
        assertEquals(16, b.stats(result.game).attack)
    }
    @Test fun gameStateRoundTripsThroughJson() {
        val game = rewarded()
        assertEquals(game, Balance.json.decodeFromString(GameState.serializer(), Balance.json.encodeToString(GameState.serializer(), game)))
    }
}
