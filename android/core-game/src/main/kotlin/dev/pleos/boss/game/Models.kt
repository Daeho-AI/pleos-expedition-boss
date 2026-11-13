package dev.pleos.boss.game

import kotlinx.serialization.Serializable

@Serializable enum class Slot { WEAPON, ARMOR, CHARM }
@Serializable enum class Rarity { COMMON, RARE, EPIC, LEGENDARY }
@Serializable enum class UxMode { PARKED_INTERACTIVE, DRIVING_LIMITED, UNKNOWN }
@Serializable enum class SessionState { ACTIVE, PAUSED, FINISHED }
@Serializable enum class BattleState { READY, FIGHTING, DATA_PAUSED, OVERSPEED_PAUSED, KNOCKED_OUT, QUOTA_REACHED }
@Serializable data class Hero(
    val level: Int = 1, val xp: Int = 0, val coins: Int = 0, val dust: Int = 0,
    val commonChestStreak: Int = 0, val autoExpeditionEnabled: Boolean = false,
    val combatRemainder: Long = 0,
    val progress: IdleProgress = IdleProgress(),
)
@Serializable data class Encounter(
    val id: String, val bossId: String, val bossIndex: Int, val cycle: Int,
    val hpMilli: Long, val maxHpMilli: Long,
)
@Serializable data class TripSession(
    val id: String, val startedAt: Long, val endedAt: Long? = null,
    val state: SessionState = SessionState.ACTIVE, val kills: Int = 0,
    val hpMilli: Long = 100_000, val eligibleMs: Long = 0, val pausedMs: Long = 0,
    val arrived: Boolean = false, val chestIds: List<String> = emptyList(),
)
@Serializable data class Chest(
    val id: String, val encounterId: String, val bossId: String, val seed: Long,
    val balanceVersion: String, val coins: Int, val templateId: String,
    val rarity: Rarity, val openedAt: Long? = null, val convertedDust: Int = 0,
)
@Serializable data class Equipment(val templateId: String, val rarity: Rarity, val upgradeLevel: Int = 0) {
    val key: String get() = templateId + ":" + rarity.name
}
@Serializable data class BattleEvent(val id: String, val sessionId: String, val type: String, val amount: Long, val at: Long)
@Serializable data class GameState(
    val hero: Hero = Hero(),
    val encounter: Encounter? = null,
    val sessions: List<TripSession> = emptyList(),
    val chests: List<Chest> = emptyList(),
    val equipment: List<Equipment> = emptyList(),
    val equipped: Map<Slot, String> = emptyMap(),
    val collection: Map<String, Int> = emptyMap(),
    val events: List<BattleEvent> = emptyList(),
    val economy: EconomyState = EconomyState(),
) {
    val activeSession: TripSession? get() = sessions.lastOrNull { it.state != SessionState.FINISHED }
    val lastFinishedSession: TripSession? get() = sessions.lastOrNull { it.state == SessionState.FINISHED }
    val unopened: List<Chest> get() = chests.filter { it.openedAt == null }
    fun withSession(session: TripSession): GameState {
        val existing = sessions.any { it.id == session.id }
        return copy(sessions = if (existing) sessions.map { if (it.id == session.id) session else it } else sessions + session)
    }
}
data class DrivingInput(
    val nowMs: Long, val wallTimeMs: Long,
    val speedKph: Double? = null, val speedAtMs: Long? = null,
    val limitKph: Int? = null, val limitAtMs: Long? = null,
    val guidanceActive: Boolean = false, val guidanceAtMs: Long? = null,
    val ux: UxMode = UxMode.UNKNOWN,
)
data class CombatStatus(val state: BattleState = BattleState.READY, val reason: String = "주차 후 자동 사냥을 시작하세요")
data class TickResult(val game: GameState, val status: CombatStatus)
data class HeroStats(val attack: Int, val maxHpMilli: Long, val counterReduction: Double)
interface IdSource { fun id(): String; fun seed(): Long }
