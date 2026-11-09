package dev.pleos.boss.game

import kotlin.math.ceil
import kotlin.math.min

/** Clock/sensor continuity is intentionally transient; never replay a process gap. */
class CombatEngine(private val balance: Balance, private val ids: IdSource) {
    private val rewards = RewardGenerator(balance)
    private var previousTick: Long? = null
    private var previousLimit: Int? = null
    private var graceUntil = 0L
    private var excessMs = 0L
    private var lastCounter: Long? = null
    private var parkedSince: Long? = null
    private var idleSince: Long? = null

    fun resetContinuity() {
        previousTick = null; previousLimit = null; graceUntil = 0
        resetOverspeed(); parkedSince = null; idleSince = null
    }
    private fun resetOverspeed() { excessMs = 0; lastCounter = null }
    private fun fresh(now: Long, at: Long?, seconds: Int): Boolean =
        at != null && at <= now && now - at <= seconds * 1000L

    fun tick(original: GameState, input: DrivingInput): TickResult {
        val previous = previousTick
        if (previous != null && input.nowMs <= previous) return TickResult(original, CombatStatus(BattleState.DATA_PAUSED, "중복 시간 표본"))
        previousTick = input.nowMs
        val gap = previous?.let { input.nowMs - it } ?: 0L
        val continuous = previous != null && gap <= balance.sampling.gapDiscardAboveSec * 1000L
        val credit = if (continuous) min(gap, balance.sampling.maxTickCreditSec * 1000L) else 0L
        if (!continuous) { resetOverspeed(); parkedSince = null; idleSince = null }
        val observedLimit = input.limitKph?.takeIf { it > 0 && fresh(input.nowMs, input.limitAtMs, balance.sampling.limitFreshSec) }
        if (observedLimit != null) {
            if (previousLimit != null && observedLimit < previousLimit!!) graceUntil = input.nowMs + balance.overspeed.limitDropGraceSec * 1000L
            previousLimit = observedLimit
        }

        var game = original
        if (game.encounter == null) game = game.copy(encounter = balance.encounter(0, 1, ids))
        if (!game.hero.autoExpeditionEnabled) {
            resetOverspeed()
            game.activeSession?.let { game = game.withSession(it.copy(state = SessionState.PAUSED)) }
            return TickResult(game, CombatStatus(BattleState.READY, "자동 원정이 꺼져 있어요"))
        }

        val guideValid = input.guidanceActive && fresh(input.nowMs, input.guidanceAtMs, balance.sampling.routeFreshSec)
        val speed = input.speedKph
        val speedValid = speed != null && speed.isFinite() && fresh(input.nowMs, input.speedAtMs, balance.sampling.speedFreshSec)
        val moving = speedValid && speed!! >= balance.sampling.movingMinKph && input.ux != UxMode.PARKED_INTERACTIVE
        if (game.activeSession == null) {
            if (!guideValid || !moving) return TickResult(game, CombatStatus(BattleState.READY, "목적지 안내와 이동이 시작되면 이어집니다"))
            game = game.withSession(TripSession(ids.id(), input.wallTimeMs, hpMilli = balance.stats(game).maxHpMilli))
            parkedSince = null; idleSince = null
            return TickResult(game, CombatStatus(BattleState.DATA_PAUSED, "새 여정의 첫 표본을 확인했어요"))
        }
        var trip = game.activeSession!!
        if (input.ux == UxMode.PARKED_INTERACTIVE && trip.arrived) {
            if (parkedSince == null) parkedSince = input.nowMs
        } else parkedSince = null
        if (input.ux == UxMode.PARKED_INTERACTIVE && !guideValid) {
            if (idleSince == null) idleSince = input.nowMs
        } else idleSince = null
        val arrivedEnd = parkedSince?.let { input.nowMs - it >= balance.session.arrivedParkedEndSec * 1000L } == true
        val idleEnd = idleSince?.let { input.nowMs - it >= balance.session.noGuidanceParkedEndSec * 1000L } == true
        if (arrivedEnd || idleEnd) {
            game = game.withSession(trip.copy(state = SessionState.FINISHED, endedAt = input.wallTimeMs))
            resetOverspeed(); parkedSince = null; idleSince = null
            return TickResult(game, CombatStatus(BattleState.READY, "여정이 저장됐어요. 보상을 확인하세요"))
        }
        fun paused(state: BattleState, reason: String): TickResult {
            resetOverspeed()
            return TickResult(game.withSession(trip.copy(state = SessionState.PAUSED, pausedMs = trip.pausedMs + credit)), CombatStatus(state, reason))
        }
        if (trip.kills >= balance.maxKillsPerSession) return paused(BattleState.QUOTA_REACHED, "이번 여정의 보스를 모두 만났어요")
        if (trip.hpMilli <= 0) return paused(BattleState.KNOCKED_OUT, "다음 여정에서 다시 준비됩니다")
        if (!guideValid) return paused(BattleState.DATA_PAUSED, "순정 내비 안내를 기다리고 있어요")
        if (!speedValid) return paused(BattleState.DATA_PAUSED, "차량 속도 연결을 기다리고 있어요")
        val limit = input.limitKph
        if (limit == null || limit <= 0 || !fresh(input.nowMs, input.limitAtMs, balance.sampling.limitFreshSec)) {
            return paused(BattleState.DATA_PAUSED, "도로 정보 연결을 기다리고 있어요")
        }
        if (input.nowMs < graceUntil) return paused(BattleState.DATA_PAUSED, "새 도로 정보를 확인하고 있어요")
        if (!moving) return paused(BattleState.READY, "이동 중에 여정이 기록됩니다")
        if (credit == 0L) return paused(BattleState.DATA_PAUSED, "새 주행 표본부터 기록합니다")
        trip = trip.copy(state = SessionState.ACTIVE)

        if (speed!! > limit) {
            if (speed >= limit + balance.overspeed.counterExcessKph) {
                excessMs += credit
                val sustained = excessMs >= balance.overspeed.sustainSec * 1000L
                val ready = lastCounter == null || input.nowMs - lastCounter!! >= balance.overspeed.cooldownSec * 1000L
                if (sustained && ready) {
                    val stats = balance.stats(game)
                    // Basis points avoid ceil(10.000000000000002) style rounding errors.
                    val fraction = (balance.overspeed.damageMaxHpFraction * 10000).toLong()
                    val keep = (10000 - (stats.counterReduction * 10000).toLong())
                    val numerator = stats.maxHpMilli * fraction * keep
                    val damage = (numerator + 99_999_999L) / 100_000_000L
                    trip = trip.copy(hpMilli = (trip.hpMilli - damage).coerceAtLeast(0))
                    game = game.copy(events = (game.events + BattleEvent(ids.id(), trip.id, "COUNTER", damage, input.wallTimeMs)).takeLast(200))
                    lastCounter = input.nowMs
                }
            } else resetOverspeed()
            trip = trip.copy(pausedMs = trip.pausedMs + credit)
            return TickResult(game.withSession(trip), CombatStatus(if (trip.hpMilli == 0L) BattleState.KNOCKED_OUT else BattleState.OVERSPEED_PAUSED, "원정 기록 대기 중"))
        }
        resetOverspeed()
        val damage = balance.stats(game).attack.toLong() * credit / balance.hero.damageDivisor
        val encounter = game.encounter!!
        val hp = (encounter.hpMilli - damage).coerceAtLeast(0)
        trip = trip.copy(eligibleMs = trip.eligibleMs + credit)
        game = game.copy(encounter = encounter.copy(hpMilli = hp))
        if (hp == 0L) {
            val existingChest = game.chests.any { it.encounterId == encounter.id }
            check(!existingChest) { "Encounter already rewarded" }
            val chest = rewards.create(encounter, game.hero.commonChestStreak, ids)
            val advanced = rewards.addExperience(game.hero)
            val nextIndex = (encounter.bossIndex + 1) % balance.bosses.size
            val nextCycle = encounter.cycle + if (nextIndex == 0) 1 else 0
            game = game.copy(
                hero = advanced.copy(commonChestStreak = if (chest.rarity == Rarity.COMMON) game.hero.commonChestStreak + 1 else 0),
                chests = game.chests + chest,
                encounter = balance.encounter(nextIndex, nextCycle, ids),
                events = (game.events + BattleEvent(ids.id(), trip.id, "BOSS_DEFEATED", 1, input.wallTimeMs)).takeLast(200),
            )
            trip = trip.copy(kills = trip.kills + 1, chestIds = trip.chestIds + chest.id)
        }
        return TickResult(game.withSession(trip), CombatStatus(
            if (trip.kills >= balance.maxKillsPerSession) BattleState.QUOTA_REACHED else BattleState.FIGHTING,
            "여정이 기록되고 있어요",
        ))
    }

    fun destinationArrived(game: GameState): GameState =
        game.activeSession?.let { game.withSession(it.copy(arrived = true)) } ?: game

    fun destinationChanged(game: GameState): GameState {
        parkedSince = null
        return game.activeSession?.let { game.withSession(it.copy(arrived = false)) } ?: game
    }
}
