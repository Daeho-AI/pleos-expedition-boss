package dev.pleos.entry.boss.data

import androidx.room.withTransaction
import dev.pleos.boss.game.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString

class GameRepository(private val db: GameDatabase, scope: CoroutineScope, private val balance: Balance, private val ids: IdSource) {
    private val json = Balance.json
    private val dao = db.game()
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(GameState())
    val state = mutable.asStateFlow()
    val ready = CompletableDeferred<Unit>()
    init {
        scope.launch {
            try {
                val loaded = db.withTransaction {
                    val hero = dao.hero()
                    if (hero == null) {
                        val game = GameState(
                            hero = Hero(coins = balance.hero.initialCoins, dust = balance.hero.initialDust),
                            encounter = balance.encounter(0, 1, ids),
                        )
                        saveDiff(GameState(), game)
                        game
                    } else GameState(
                        hero = json.decodeFromString(hero.payload),
                        encounter = dao.encounter()?.let { json.decodeFromString<Encounter>(it.payload) },
                        sessions = dao.sessions().map { json.decodeFromString<TripSession>(it.payload) },
                        chests = dao.chests().map { json.decodeFromString<Chest>(it.payload) },
                        equipment = dao.equipment().map { json.decodeFromString<Equipment>(it.payload) },
                        equipped = dao.equipped().associate { Slot.valueOf(it.slot) to it.itemKey },
                        collection = dao.collection().associate { it.bossId to it.fragments },
                        events = dao.events().map { json.decodeFromString<BattleEvent>(it.payload) },
                        economy = dao.economy()?.let { json.decodeFromString<EconomyState>(it.payload) } ?: EconomyState(),
                    )
                }
                mutable.value = loaded
                ready.complete(Unit)
            } catch (error: Throwable) {
                ready.completeExceptionally(error)
                if (error is kotlinx.coroutines.CancellationException) throw error
            }
        }
    }
    suspend fun change(actionId: String? = null, signature: String = "", transform: (GameState) -> ActionResult): ActionResult {
        ready.await()
        return mutex.withLock {
            // Cancellation must not leave the in-memory checkpoint behind an already committed reward.
            withContext(NonCancellable) {
                val before = mutable.value
                val result = db.withTransaction {
                    val receipt = actionId?.let { dao.action(it) }
                    if (receipt != null) {
                        if (receipt.signature != signature) return@withTransaction ActionResult(before, "동일 작업 ID의 내용이 달라요", false)
                        return@withTransaction ActionResult(before, receipt.message, receipt.success)
                    }
                    val result = transform(before)
                    if (result.game != before) saveDiff(before, result.game)
                    if (actionId != null) dao.put(ActionRow(actionId, signature, result.message, result.success, System.currentTimeMillis()))
                    result
                }
                mutable.value = result.game
                result
            }
        }
    }
    private suspend fun saveDiff(before: GameState, next: GameState) {
        if (before.hero != next.hero || dao.hero() == null) dao.put(HeroRow(payload = json.encodeToString(next.hero)))
        if (before.economy != next.economy) dao.put(EconomyRow(payload = json.encodeToString(next.economy)))
        for (item in before.equipment) if (next.equipment.none { it.key == item.key }) dao.deleteEquipment(item.key)
        for (slot in before.equipped.keys) if (slot !in next.equipped.keys) dao.deleteEquipped(slot.name)
        for (chest in before.chests) if (next.chests.none { it.id == chest.id }) dao.deleteChest(chest.id)
        for (session in before.sessions) if (next.sessions.none { it.id == session.id }) dao.deleteSession(session.id)
        for (boss in before.collection.keys) if (boss !in next.collection.keys) dao.deleteCollection(boss)
        if (before.encounter != next.encounter) {
            val oldEncounter = before.encounter
            if (oldEncounter != null && oldEncounter.id != next.encounter?.id) {
                dao.put(EncounterRow(oldEncounter.id, false, json.encodeToString(oldEncounter.copy(hpMilli = 0))))
            }
            next.encounter?.let { dao.put(EncounterRow(it.id, true, json.encodeToString(it))) }
        }
        for (s in next.sessions) if (before.sessions.find { it.id == s.id } != s) dao.put(SessionRow(s.id, s.startedAt, json.encodeToString(s)))
        for (c in next.chests) if (before.chests.find { it.id == c.id } != c) dao.put(ChestRow(c.id, c.encounterId, json.encodeToString(c)))
        for (e in next.equipment) if (before.equipment.find { it.key == e.key } != e) dao.put(EquipmentRow(e.key, json.encodeToString(e)))
        for ((slot, key) in next.equipped) if (before.equipped[slot] != key) dao.put(EquippedRow(slot.name, key))
        for ((boss, count) in next.collection) if (before.collection[boss] != count) dao.put(CollectionRow(boss, count))
        for (event in next.events) if (before.events.none { it.id == event.id }) dao.put(EventRow(event.id, event.at, json.encodeToString(event)))
        if (next.events.size < before.events.size || before.events.firstOrNull()?.id != next.events.firstOrNull()?.id) dao.trimEvents(next.events.map { it.id })
    }
}
