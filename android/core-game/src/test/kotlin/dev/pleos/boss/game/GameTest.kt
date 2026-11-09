package dev.pleos.boss.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestIds : IdSource {
    private var sequence = 0L
    override fun id(): String = "test-" + (++sequence)
    override fun seed(): Long = ++sequence * 761
}
class GameTest {
    private val b = Balance.parse(javaClass.getResource("/balance.json")!!.readText())
    private class Run(val b: Balance) {
        val ids = TestIds()
        var engine = CombatEngine(b, ids)
        var game = GameState(hero = Hero(autoExpeditionEnabled = true))
        var time = 0L
        var result = TickResult(game, CombatStatus())
        fun tick(speed: Double? = 40.0, limit: Int? = 60, guide: Boolean = true, ux: UxMode = UxMode.DRIVING_LIMITED, speedAge: Long = 0, limitAge: Long = 0, delta: Long = 1000) {
            time += delta
            result = engine.tick(game, DrivingInput(time, time + 1_700_000_000_000, speed, speed?.let { time - speedAge }, limit, limit?.let { time - limitAge }, guide, time, ux))
            game = result.game
        }
        fun drive(seconds: Int, speed: Double = 40.0) { repeat(seconds) { tick(speed = speed) } }
        fun finish() {
            game = engine.destinationArrived(game)
            repeat(31) { tick(speed = 0.0, guide = false, ux = UxMode.PARKED_INTERACTIVE) }
        }
    }
    @Test fun sixtySecondsDealsSixtyHp() {
        val r = Run(b); r.tick(); r.drive(60)
        assertEquals(120_000, r.game.encounter!!.hpMilli)
        assertEquals(60_000, r.game.activeSession!!.eligibleMs)
    }
    @Test fun fasterWithinLimitDoesNotIncreaseDamage() {
        val a = Run(b); val c = Run(b); a.tick(); c.tick()
        a.drive(60, 20.0); c.drive(60, 60.0)
        assertEquals(a.game.encounter!!.hpMilli, c.game.encounter!!.hpMilli)
    }
    @Test fun hpCarriesBetweenTrips() {
        val r = Run(b); r.tick(); r.drive(60)
        val encounter = r.game.encounter!!.id
        r.finish(); r.tick(); r.drive(60)
        assertEquals(encounter, r.game.encounter!!.id)
        assertEquals(60_000, r.game.encounter!!.hpMilli)
        assertEquals(2, r.game.sessions.size)
    }
    @Test fun missingLimitNeverAttacksOrCounters() {
        val r = Run(b); r.tick()
        repeat(60) { r.tick(speed = 100.0, limit = null) }
        assertEquals(180_000, r.game.encounter!!.hpMilli)
        assertEquals(100_000, r.game.activeSession!!.hpMilli)
        assertEquals(BattleState.DATA_PAUSED, r.result.status.state)
    }
    @Test fun staleSpeedAndStaleLimitBlockCombat() {
        val r = Run(b); r.tick()
        repeat(30) { r.tick(speedAge = 2001) }
        repeat(30) { r.tick(limitAge = 3001) }
        assertEquals(180_000, r.game.encounter!!.hpMilli)
    }
    @Test fun reverseAndStationaryNeverDamage() {
        val r = Run(b); r.tick()
        repeat(20) { r.tick(speed = -10.0) }; repeat(20) { r.tick(speed = 0.0) }
        assertEquals(180_000, r.game.encounter!!.hpMilli)
    }
    @Test fun counterAtFiveAndTwentySeconds() {
        val r = Run(b); r.tick(speed = 70.0)
        r.drive(4, 70.0); assertEquals(100_000, r.game.activeSession!!.hpMilli)
        r.drive(1, 70.0); assertEquals(90_000, r.game.activeSession!!.hpMilli)
        r.drive(14, 70.0); assertEquals(90_000, r.game.activeSession!!.hpMilli)
        r.drive(1, 70.0); assertEquals(80_000, r.game.activeSession!!.hpMilli)
        assertEquals(180_000, r.game.encounter!!.hpMilli)
    }
    @Test fun missingDataResetsOverspeedStreak() {
        val r = Run(b); r.tick(speed = 70.0); r.drive(4, 70.0)
        r.tick(speed = 70.0, limit = null); r.drive(4, 70.0)
        assertEquals(100_000, r.game.activeSession!!.hpMilli)
    }
    @Test fun limitDropHasFiveSecondGrace() {
        val r = Run(b); r.tick(speed = 40.0, limit = 80)
        r.tick(speed = 70.0, limit = 60)
        repeat(4) { r.tick(speed = 70.0, limit = 60) }
        assertEquals(100_000, r.game.activeSession!!.hpMilli)
        assertEquals(180_000, r.game.encounter!!.hpMilli)
        assertEquals(BattleState.DATA_PAUSED, r.result.status.state)
    }
    @Test fun twoKillsAreAHardPerTripLimit() {
        val r = Run(b); r.tick(); r.drive(900)
        assertEquals(2, r.game.activeSession!!.kills)
        assertEquals(2, r.game.chests.size)
        val hp = r.game.encounter!!.hpMilli
        r.drive(1200)
        assertEquals(hp, r.game.encounter!!.hpMilli)
        assertEquals(2, r.game.chests.size)
    }
    @Test fun duplicateTicksDoNotDuplicateRewards() {
        val r = Run(b); r.tick(); r.drive(180)
        val after = r.game
        r.tick(delta = 0)
        assertEquals(after, r.game)
        assertEquals(1, r.game.chests.size)
    }
    @Test fun processRecreationNeverCreditsElapsedTime() {
        val r = Run(b); r.tick(); r.drive(60)
        val id = r.game.activeSession!!.id
        r.engine = CombatEngine(b, r.ids)
        r.tick(delta = 600_000)
        assertEquals(id, r.game.activeSession!!.id)
        assertEquals(120_000, r.game.encounter!!.hpMilli)
        r.drive(1)
        assertEquals(119_000, r.game.encounter!!.hpMilli)
    }
    @Test fun gapIsDiscardedButTwoSecondsCreditsAtMostOne() {
        val r = Run(b); r.tick()
        r.tick(delta = 2000); assertEquals(179_000, r.game.encounter!!.hpMilli)
        r.tick(delta = 2001); assertEquals(179_000, r.game.encounter!!.hpMilli)
    }
    @Test fun knockedOutCannotRecoverWithinSameTrip() {
        val r = Run(b); r.tick(speed = 70.0); r.drive(160, 70.0)
        assertEquals(0, r.game.activeSession!!.hpMilli)
        r.drive(60); assertEquals(180_000, r.game.encounter!!.hpMilli)
        r.finish(); r.tick()
        assertEquals(100_000, r.game.activeSession!!.hpMilli)
    }
    @Test fun chestIsGrantedExactlyOnceAndDuplicatesBecomeDust() {
        val r = Run(b); r.tick(); r.drive(180)
        val actions = GameActions(b); val chest = r.game.chests.single()
        val opened = actions.openChest(r.game, chest.id, 1, UxMode.PARKED_INTERACTIVE)
        val second = actions.openChest(opened.game, chest.id, 2, UxMode.PARKED_INTERACTIVE)
        assertEquals(opened.game, second.game)
        val duplicate = chest.copy(id = "another", encounterId = "another-encounter", openedAt = null)
        val result = actions.openChest(opened.game.copy(chests = opened.game.chests + duplicate), duplicate.id, 3, UxMode.PARKED_INTERACTIVE)
        assertEquals(1, result.game.equipment.size)
        assertEquals(b.chest.duplicateDust[chest.rarity], result.game.hero.dust)
        assertEquals(2, result.game.collection[chest.bossId])
    }
    @Test fun pityUsesChestGenerationNotOpeningOrder() {
        val generator = RewardGenerator(b); val ids = TestIds()
        val encounter = b.encounter(0, 1, ids)
        repeat(500) {
            val chest = generator.create(encounter, 9, ids)
            assertTrue(chest.rarity != Rarity.COMMON)
        }
    }
    @Test fun nonParkedMutationsFailWithoutChangingState() {
        val r = Run(b); r.tick(); r.drive(180)
        val result = GameActions(b).openChest(r.game, r.game.chests.single().id, 0, UxMode.UNKNOWN)
        assertFalse(result.success); assertEquals(r.game, result.game)
    }
    @Test fun upgradeCostsAndGearStatsFollowBalance() {
        val item = Equipment("sand_hammer", Rarity.EPIC, 1)
        val game = GameState(hero = Hero(coins = 100, dust = 3), equipment = listOf(item), equipped = mapOf(Slot.WEAPON to item.key))
        val result = GameActions(b).upgrade(game, item.key, UxMode.PARKED_INTERACTIVE)
        assertEquals(40, result.game.hero.coins); assertEquals(1, result.game.hero.dust)
        assertEquals(16, b.stats(result.game).attack)
    }
    @Test fun gameStateRoundTripsThroughJson() {
        val r = Run(b); r.tick(); r.drive(420); r.finish()
        val json = Balance.json
        val encoded = json.encodeToString(GameState.serializer(), r.game)
        assertEquals(r.game, json.decodeFromString(GameState.serializer(), encoded))
    }
    @Test fun repeatedArrivalDoesNotAddSessionOrChest() {
        val r = Run(b); r.tick(); r.drive(60)
        repeat(10) { r.game = r.engine.destinationArrived(r.game) }
        r.finish()
        assertEquals(1, r.game.sessions.size)
        assertEquals(0, r.game.chests.size)
    }
}
