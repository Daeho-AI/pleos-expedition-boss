package dev.pleos.boss.game

import kotlinx.serialization.Serializable

@Serializable data class ReturnSummary(
    val id: String, val at: Long, val elapsedMs: Long, val creditedMs: Long,
    val bosses: Int, val chests: Int,
    val distanceMeters: Long = 0, val creditedDistanceMeters: Long = 0,
    val strikeDamageMilli: Long = 0, val strikeBossIndex: Int = 0,
    val distanceChecked: Boolean = false,
)

/** Stored inside the hero row, in the same Room transaction as HP, XP and chests. */
@Serializable data class IdleProgress(
    val accountedWallMs: Long? = null,
    val accountedElapsedMs: Long? = null,
    val bootId: Int? = null,
    val paused: Boolean = true,
    val distanceBaselineMeters: Long? = null,
    val distanceSource: String? = null,
    val awaitingDistance: Boolean = false,
    val departureAttack: Int = 0,
    val lastReturn: ReturnSummary? = null,
)

data class ProgressClock(val wallMs: Long, val elapsedMs: Long, val bootId: Int?)
data class DistanceSample(val meters: Long, val atElapsedMs: Long, val source: String)
data class ProgressInput(
    val clock: ProgressClock, val ux: UxMode, val foreground: Boolean,
    val distance: DistanceSample? = null,
)

class IdleEngine(private val balance: Balance, private val ids: IdSource) {
    private val combat = AutoCombat(balance, ids)

    fun pause(original: GameState, at: Long): GameState {
        val p = original.hero.progress
        if (p.paused) return original
        var game = original.copy(hero = original.hero.copy(progress = p.copy(
            paused = true, awaitingDistance = original.hero.autoExpeditionEnabled,
            departureAttack = balance.stats(original).attack,
        )))
        game.activeSession?.let { game = game.withSession(it.copy(state = SessionState.FINISHED, endedAt = at)) }
        return game
    }

    fun setEnabled(original: GameState, enabled: Boolean, input: ProgressInput): GameState {
        if (!canPlay(input) || original.hero.autoExpeditionEnabled == enabled) return original
        if (!enabled) {
            val settled = tick(original, input).game
            return pause(settled, input.clock.wallMs).let { it.copy(hero = it.hero.copy(
                autoExpeditionEnabled = false,
                progress = it.hero.progress.copy(accountedWallMs = null, accountedElapsedMs = null,
                    awaitingDistance = false, distanceBaselineMeters = null, distanceSource = null),
            )) }
        }
        val sample = validDistance(input)
        return original.copy(hero = original.hero.copy(autoExpeditionEnabled = true,
            progress = checkpoint(IdleProgress(
                distanceBaselineMeters = sample?.meters, distanceSource = sample?.source,
                awaitingDistance = sample == null, departureAttack = balance.stats(original).attack,
            ), input.clock)))
    }

    fun tick(original: GameState, input: ProgressInput): TickResult {
        if (!canPlay(input)) return TickResult(pause(original, input.clock.wallMs),
            CombatStatus(BattleState.DATA_PAUSED, "주행 중에는 게임이 중단됩니다. 주차 후 돌아오면 이어집니다"))
        if (!original.hero.autoExpeditionEnabled) return TickResult(original,
            CombatStatus(BattleState.READY, "자동 사냥을 켜면 주차 중 전투가 이어집니다"))
        val p = original.hero.progress
        val first = p.accountedWallMs == null
        val elapsed = if (first) 0L else elapsed(p, input.clock)
        val returning = p.paused || elapsed > MAX_LIVE_GAP_MS
        val credit = elapsed.coerceAtMost(if (returning) MAX_OFFLINE_MS else MAX_LIVE_GAP_MS)
        var game = combat.advance(original, credit, input.clock.wallMs)
        val report = if (returning && !first) ReturnSummary(
            ids.id(), input.clock.wallMs, elapsed, credit,
            game.chests.size - original.chests.size, game.chests.size - original.chests.size,
        ) else p.lastReturn
        game = game.copy(hero = game.hero.copy(progress = checkpoint(p.copy(
            lastReturn = report,
            awaitingDistance = p.awaitingDistance || returning || first,
            departureAttack = p.departureAttack.takeIf { it > 0 } ?: balance.stats(original).attack,
        ), input.clock)))
        game = applyDistance(game, input)
        return TickResult(game, CombatStatus(BattleState.FIGHTING, "주차 중 자동 사냥 · 비운 시간은 복귀할 때 정산됩니다"))
    }

    private fun applyDistance(original: GameState, input: ProgressInput): GameState {
        val p = original.hero.progress
        if (!p.awaitingDistance) return original
        val sample = validDistance(input) ?: return original
        val baseline = p.distanceBaselineMeters
        val distance = if (baseline != null && p.distanceSource == sample.source && sample.meters >= baseline)
            sample.meters - baseline else 0L
        val credited = distance.coerceAtMost(MAX_STRIKE_METERS)
        val damage = credited * p.departureAttack.coerceAtLeast(1) * DAMAGE_PER_KM_ATTACK_MULTIPLIER
        val target = original.encounter
        val actualDamage = minOf(damage, target?.hpMilli ?: 0L)
        var game = if (actualDamage > 0) combat.strike(original, actualDamage, input.clock.wallMs) else original
        val report = p.lastReturn?.copy(
            distanceMeters = distance, creditedDistanceMeters = credited,
            strikeDamageMilli = actualDamage, strikeBossIndex = target?.bossIndex ?: 0,
            bosses = p.lastReturn.bosses + game.chests.size - original.chests.size,
            chests = p.lastReturn.chests + game.chests.size - original.chests.size,
            distanceChecked = true,
        )
        // Consume the complete reading even when capped; reopening cannot spend the excess again.
        game = game.copy(hero = game.hero.copy(progress = p.copy(
            distanceBaselineMeters = sample.meters, distanceSource = sample.source,
            awaitingDistance = false, lastReturn = report,
        )))
        return game
    }

    private fun validDistance(input: ProgressInput): DistanceSample? = input.distance?.takeIf {
        it.meters >= 0 && it.source.isNotBlank() && input.clock.elapsedMs - it.atElapsedMs in 0..5_000
    }
    private fun elapsed(p: IdleProgress, now: ProgressClock): Long {
        val previous = p.accountedElapsedMs
        return if (now.bootId != null && now.bootId == p.bootId && previous != null)
            (now.elapsedMs - previous).coerceAtLeast(0)
        else (now.wallMs - (p.accountedWallMs ?: now.wallMs)).coerceAtLeast(0)
    }
    private fun checkpoint(p: IdleProgress, now: ProgressClock) = p.copy(
        accountedWallMs = maxOf(p.accountedWallMs ?: now.wallMs, now.wallMs),
        accountedElapsedMs = if (now.bootId != null && now.bootId == p.bootId)
            maxOf(p.accountedElapsedMs ?: now.elapsedMs, now.elapsedMs) else now.elapsedMs,
        bootId = now.bootId, paused = false,
    )
    private fun canPlay(input: ProgressInput) = input.foreground && input.ux == UxMode.PARKED_INTERACTIVE

    companion object {
        const val MAX_OFFLINE_MS = AutoCombat.MAX_ADVANCE_MS
        const val MAX_LIVE_GAP_MS = 2_500L
        const val MAX_STRIKE_METERS = 30_000L
        const val DAMAGE_PER_KM_ATTACK_MULTIPLIER = 2L
    }
}
