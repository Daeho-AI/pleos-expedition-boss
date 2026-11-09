package dev.pleos.entry.boss.data

import androidx.room.*

@Entity(tableName = "hero") data class HeroRow(@PrimaryKey val id: String = "local", val payload: String)
@Entity(tableName = "encounter") data class EncounterRow(@PrimaryKey val id: String, val active: Boolean, val payload: String)
@Entity(tableName = "trip_session") data class SessionRow(@PrimaryKey val id: String, val startedAt: Long, val payload: String)
@Entity(tableName = "chest", indices = [Index(value = ["encounterId"], unique = true)])
data class ChestRow(@PrimaryKey val id: String, val encounterId: String, val payload: String)
@Entity(tableName = "equipment") data class EquipmentRow(@PrimaryKey val itemKey: String, val payload: String)
@Entity(tableName = "equipped") data class EquippedRow(@PrimaryKey val slot: String, val itemKey: String)
@Entity(tableName = "collection") data class CollectionRow(@PrimaryKey val bossId: String, val fragments: Int)
@Entity(tableName = "battle_event") data class EventRow(@PrimaryKey val id: String, val at: Long, val payload: String)
@Entity(tableName = "processed_action") data class ActionRow(@PrimaryKey val id: String, val signature: String, val message: String, val success: Boolean, val at: Long)

@Dao interface GameDao {
    @Query("SELECT * FROM hero WHERE id='local'") suspend fun hero(): HeroRow?
    @Query("SELECT * FROM encounter WHERE active=1 LIMIT 1") suspend fun encounter(): EncounterRow?
    @Query("SELECT * FROM trip_session ORDER BY startedAt,id") suspend fun sessions(): List<SessionRow>
    @Query("SELECT * FROM chest ORDER BY rowid") suspend fun chests(): List<ChestRow>
    @Query("SELECT * FROM equipment ORDER BY rowid") suspend fun equipment(): List<EquipmentRow>
    @Query("SELECT * FROM equipped") suspend fun equipped(): List<EquippedRow>
    @Query("SELECT * FROM collection") suspend fun collection(): List<CollectionRow>
    @Query("SELECT * FROM battle_event ORDER BY at,id") suspend fun events(): List<EventRow>
    @Query("SELECT * FROM processed_action WHERE id=:id") suspend fun action(id: String): ActionRow?
    @Upsert suspend fun put(row: HeroRow)
    @Upsert suspend fun put(row: EncounterRow)
    @Upsert suspend fun put(row: SessionRow)
    @Query("SELECT * FROM chest WHERE id=:id") suspend fun chest(id: String): ChestRow?
    @Insert suspend fun insertChest(row: ChestRow)
    @Update suspend fun updateChest(row: ChestRow)
    // Room's generic upsert can swallow a conflict on a secondary unique key.
    // A new reward must fail loudly if its encounter already owns another chest.
    @Transaction suspend fun put(row: ChestRow) {
        if (chest(row.id) == null) insertChest(row) else updateChest(row)
    }
    @Upsert suspend fun put(row: EquipmentRow)
    @Upsert suspend fun put(row: EquippedRow)
    @Upsert suspend fun put(row: CollectionRow)
    @Upsert suspend fun put(row: EventRow)
    @Insert suspend fun put(row: ActionRow)
    @Query("DELETE FROM battle_event WHERE id NOT IN (:ids)") suspend fun trimEvents(ids: List<String>)
}

@Database(entities = [HeroRow::class, EncounterRow::class, SessionRow::class, ChestRow::class,
    EquipmentRow::class, EquippedRow::class, CollectionRow::class, EventRow::class, ActionRow::class], version = 1, exportSchema = false)
abstract class GameDatabase : RoomDatabase() { abstract fun game(): GameDao }
