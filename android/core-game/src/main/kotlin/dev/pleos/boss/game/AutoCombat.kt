package dev.pleos.boss.game

/** Pure combat calculation. The caller owns foreground/vehicle eligibility and time accounting. */
class AutoCombat(private val balance: Balance, private val ids: IdSource) {
    private val rewards = RewardGenerator(balance)

    fun advance(original: GameState, elapsedMs: Long, at: Long): GameState {
        require(elapsedMs in 0..MAX_ADVANCE_MS)
        if (!original.hero.autoExpeditionEnabled || elapsedMs == 0L) return original
        var game = original
        if (game.encounter == null) game = game.copy(encounter = balance.encounter(0, 1, ids))
        var session = game.activeSession ?: TripSession(ids.id(), at - elapsedMs,
            hpMilli = balance.stats(game).maxHpMilli)
        session = session.copy(state = SessionState.ACTIVE)
        var remaining = elapsedMs
        while (remaining > 0) {
            val encounter = game.encounter!!
            val rate = balance.stats(game).attack.toLong().coerceAtLeast(1)
            val divisor = balance.hero.damageDivisor.toLong()
            val remainder = game.hero.combatRemainder.coerceIn(0, divisor - 1)
            val needed = (encounter.hpMilli * divisor - remainder).coerceAtLeast(1)
            val untilDefeat = (needed + rate - 1) / rate
            val consumed = minOf(remaining, untilDefeat)
            val numerator = consumed * rate + remainder
            val hp = (encounter.hpMilli - numerator / divisor).coerceAtLeast(0)
            remaining -= consumed
            game = game.copy(
                hero = game.hero.copy(combatRemainder = numerator % divisor),
                encounter = encounter.copy(hpMilli = hp),
            )
            if (hp == 0L) {
                check(game.chests.none { it.encounterId == encounter.id }) { "Encounter already rewarded" }
                val chest = rewards.create(encounter, game.hero.commonChestStreak, ids)
                val nextIndex = (encounter.bossIndex + 1) % balance.bosses.size
                val nextCycle = encounter.cycle + if (nextIndex == 0) 1 else 0
                game = game.copy(
                    hero = rewards.addExperience(game.hero).copy(
                        commonChestStreak = if (chest.rarity == Rarity.COMMON) game.hero.commonChestStreak + 1 else 0,
                        combatRemainder = 0,
                    ),
                    chests = game.chests + chest,
                    encounter = balance.encounter(nextIndex, nextCycle, ids),
                    events = (game.events + BattleEvent(ids.id(), session.id, "BOSS_DEFEATED", 1, at - remaining)).takeLast(200),
                )
                session = session.copy(kills = session.kills + 1, chestIds = session.chestIds + chest.id)
            }
        }
        return game.withSession(session.copy(eligibleMs = session.eligibleMs + elapsedMs))
    }

    companion object { const val MAX_ADVANCE_MS = 8 * 60 * 60 * 1000L }

    /** One automatic impact on the current boss. Excess damage never spills into another boss. */
    fun strike(original: GameState, damageMilli: Long, at: Long): GameState {
        if (!original.hero.autoExpeditionEnabled || damageMilli <= 0) return original
        val encounter = original.encounter ?: return original
        val damage = minOf(damageMilli, encounter.hpMilli)
        var session = original.activeSession ?: TripSession(ids.id(), at,
            hpMilli = balance.stats(original).maxHpMilli)
        var game = original.copy(encounter = encounter.copy(hpMilli = encounter.hpMilli - damage),
            events = (original.events + BattleEvent(ids.id(), session.id, "FINISHING_STRIKE", damage, at)).takeLast(200))
        if (game.encounter!!.hpMilli == 0L) {
            check(game.chests.none { it.encounterId == encounter.id }) { "Encounter already rewarded" }
            val chest = rewards.create(encounter, game.hero.commonChestStreak, ids)
            val nextIndex = (encounter.bossIndex + 1) % balance.bosses.size
            game = game.copy(
                hero = rewards.addExperience(game.hero).copy(
                    commonChestStreak = if (chest.rarity == Rarity.COMMON) game.hero.commonChestStreak + 1 else 0,
                    combatRemainder = 0,
                ),
                chests = game.chests + chest,
                encounter = balance.encounter(nextIndex, encounter.cycle + if (nextIndex == 0) 1 else 0, ids),
                events = (game.events + BattleEvent(ids.id(), session.id, "BOSS_DEFEATED", 1, at)).takeLast(200),
            )
            session = session.copy(kills = session.kills + 1, chestIds = session.chestIds + chest.id)
        }
        return game.withSession(session)
    }
}
