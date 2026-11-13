package dev.pleos.boss.game

import kotlin.test.*

class IdleEngineTest {
    private val balance = Balance.parse(javaClass.getResource("/balance.json")!!.readText())
    private val ids = TestIds()
    private val engine = IdleEngine(balance, ids)
    private fun input(ms: Long, ux: UxMode = UxMode.PARKED_INTERACTIVE, foreground: Boolean = true,
        meters: Long? = 100_000, wall: Long = 1_800_000_000_000L + ms, boot: Int? = 7) =
        ProgressInput(ProgressClock(wall, ms, boot), ux, foreground,
            meters?.let { DistanceSample(it, ms, "ODOMETER_KM") })
    private fun start() = engine.setEnabled(GameState(encounter = balance.encounter(0, 1, ids)), true, input(0))

    @Test fun parkedAutoHuntContinuesWithoutNavigation() {
        var game = start()
        repeat(60) { game = engine.tick(game, input((it + 1) * 1_000L)).game }
        assertEquals(120_000, game.encounter!!.hpMilli)
        assertFalse(game.hero.progress.paused)
    }
    @Test fun drivingUnknownAndBackgroundNeverCalculateCombat() {
        for ((ux, foreground) in listOf(UxMode.DRIVING_LIMITED to true, UxMode.UNKNOWN to true, UxMode.PARKED_INTERACTIVE to false)) {
            val initial = start()
            var paused = engine.tick(initial, input(1_000, ux, foreground)).game
            repeat(420) { paused = engine.tick(paused, input((it + 2) * 1_000L, ux, foreground, 110_000)).game }
            assertEquals(initial.encounter, paused.encounter)
            assertEquals(initial.chests, paused.chests)
            assertTrue(paused.hero.progress.paused)
        }
    }
    @Test fun returningSettlesOnceThenLiveCombatContinues() {
        val paused = engine.pause(start(), input(0).clock.wallMs)
        val returned = engine.tick(paused, input(60_000)).game
        assertEquals(120_000, returned.encounter!!.hpMilli)
        assertEquals(60_000, returned.hero.progress.lastReturn!!.creditedMs)
        assertEquals(returned, engine.tick(returned, input(60_000)).game)
        assertEquals(119_000, engine.tick(returned, input(61_000)).game.encounter!!.hpMilli)
    }
    @Test fun distanceStrikeUsesDepartureAttackAndOnlyHitsOnce() {
        val paused = engine.pause(start(), input(0).clock.wallMs)
        val returned = engine.tick(paused, input(60_000, meters = 102_000)).game
        assertEquals(80_000, returned.encounter!!.hpMilli)
        assertEquals(40_000, returned.hero.progress.lastReturn!!.strikeDamageMilli)
        assertEquals(2_000, returned.hero.progress.lastReturn!!.distanceMeters)
        val reopened = engine.tick(engine.pause(returned, input(60_000).clock.wallMs), input(60_000, meters = 102_000)).game
        assertEquals(returned.encounter, reopened.encounter)
        assertEquals(0, reopened.hero.progress.lastReturn!!.strikeDamageMilli)
    }
    @Test fun hugeStrikeKillsOnlyCurrentBossAndConsumesCappedDistance() {
        val returned = engine.tick(engine.pause(start(), 0), input(1_000, meters = 200_000)).game
        assertEquals(1, returned.chests.size)
        assertEquals(240_000, returned.encounter!!.hpMilli)
        assertEquals(30_000, returned.hero.progress.lastReturn!!.creditedDistanceMeters)
        assertEquals(200_000, returned.hero.progress.distanceBaselineMeters)
        val reopened = engine.tick(engine.pause(returned, 0), input(1_000, meters = 200_000)).game
        assertEquals(1, reopened.chests.size)
        assertEquals(0, reopened.hero.progress.lastReturn!!.strikeDamageMilli)
    }
    @Test fun missingDistanceDoesNotBlockGrowthAndLateReadingCannotDuplicateLoot() {
        val returned = engine.tick(engine.pause(start(), 0), input(60_000, meters = null)).game
        assertEquals(120_000, returned.encounter!!.hpMilli)
        assertTrue(returned.hero.progress.awaitingDistance)
        val late = engine.tick(returned, input(60_000, meters = 102_000)).game
        assertEquals(80_000, late.encounter!!.hpMilli)
        assertEquals(late, engine.tick(late, input(60_000, meters = 102_000)).game)
    }
    @Test fun firstReadingNeverRewardsVehicleLifetimeMileage() {
        val initial = engine.setEnabled(GameState(encounter = balance.encounter(0, 1, ids)), true, input(0, meters = null))
        val returned = engine.tick(initial, input(1_000, meters = 999_000_000)).game
        assertEquals(179_000, returned.encounter!!.hpMilli)
        assertEquals(999_000_000, returned.hero.progress.distanceBaselineMeters)
        assertTrue(returned.chests.isEmpty())
    }
    @Test fun resetOrStaleOrDifferentSourceCannotBecomeBonus() {
        val paused = engine.pause(start(), 0)
        val reset = engine.tick(paused, input(0, meters = 5_000)).game
        assertEquals(paused.encounter, reset.encounter)
        assertEquals(5_000, reset.hero.progress.distanceBaselineMeters)
        val stale = input(10_000, meters = 110_000).copy(distance = DistanceSample(110_000, 0, "ODOMETER_KM"))
        assertTrue(engine.tick(paused, stale).game.hero.progress.awaitingDistance)
        val other = input(0).copy(distance = DistanceSample(200_000, 0, "OTHER_SOURCE"))
        assertEquals(paused.encounter, engine.tick(paused, other).game.encounter)
    }
    @Test fun offlineCapDiscardsExcessInsteadOfPayingOnEveryReopen() {
        val returned = engine.tick(engine.pause(start(), 0), input(24 * 60 * 60 * 1_000L)).game
        assertEquals(IdleEngine.MAX_OFFLINE_MS, returned.hero.progress.lastReturn!!.creditedMs)
        val again = engine.tick(engine.pause(returned, 0), input(24 * 60 * 60 * 1_000L)).game
        assertEquals(returned.encounter, again.encounter)
        assertEquals(returned.chests, again.chests)
        assertEquals(0, again.hero.progress.lastReturn!!.creditedMs)
    }
    @Test fun disabledTimeAndOldSaveAreNotRetroactivelyCredited() {
        val off = engine.setEnabled(start(), false, input(0))
        val again = engine.setEnabled(off, true, input(100_000, meters = 150_000))
        assertEquals(off.encounter, again.encounter)
        assertEquals(150_000, again.hero.progress.distanceBaselineMeters)
        val legacy = GameState(hero = Hero(autoExpeditionEnabled = true), encounter = balance.encounter(0, 1, ids))
        assertEquals(legacy.encounter, engine.tick(legacy, input(9_999_999)).game.encounter)
    }
    @Test fun monotonicClockPreventsWallClockBoostAndRewindDuplication() {
        val once = engine.tick(start(), input(1_000, wall = 1_900_000_000_000L)).game
        assertEquals(179_000, once.encounter!!.hpMilli)
        val rewind = engine.tick(once, input(0, wall = 1_700_000_000_000L)).game
        val repeat = engine.tick(rewind, input(1_000, wall = 1_900_000_000_000L)).game
        assertEquals(once.encounter, repeat.encounter)
    }
    @Test fun rebootUsesWallClockButNeverCreditsAClockRewind() {
        val paused = engine.pause(start(), 0)
        val returned = engine.tick(paused, input(100, wall = 1_800_000_060_000L, boot = 8)).game
        assertEquals(120_000, returned.encounter!!.hpMilli)
        val backward = engine.tick(paused, input(100, wall = 1_700_000_000_000L, boot = 8)).game
        assertEquals(paused.encounter, backward.encounter)
    }
    @Test fun serializedCheckpointAndDistanceSurviveEngineRecreation() {
        val returned = engine.tick(engine.pause(start(), 0), input(60_000, meters = 102_000)).game
        val restored = Balance.json.decodeFromString(GameState.serializer(), Balance.json.encodeToString(GameState.serializer(), returned))
        assertEquals(returned, IdleEngine(balance, TestIds()).tick(restored, input(60_000, meters = 102_000)).game)
    }
}
