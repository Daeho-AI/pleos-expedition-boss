package dev.pleos.boss.game

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

@Serializable data class HeroTuning(
    val baseHp: Int, val baseAttack: Int, val attackPerLevel: Int, val damageDivisor: Int,
    val xpPerBoss: Int, val xpToNextLevelMultiplier: Int, val maxLevel: Int,
    val initialCoins: Int, val initialDust: Int, val initialEquipment: List<String>,
)
@Serializable data class SamplingTuning(
    val tickSec: Int, val maxTickCreditSec: Int, val gapDiscardAboveSec: Int,
    val speedFreshSec: Int, val limitFreshSec: Int, val routeFreshSec: Int, val movingMinKph: Int,
)
@Serializable data class OverspeedTuning(
    val attackPauseAboveLimit: Boolean, val counterExcessKph: Int,
    val sustainSec: Int, val cooldownSec: Int, val damageMaxHpFraction: Double,
    val maxArmorReductionFraction: Double, val limitDropGraceSec: Int,
    val armorReductionByRarity: Map<Rarity, Double>,
)
@Serializable data class SessionTuning(val arrivedParkedEndSec: Int, val noGuidanceParkedEndSec: Int)
@Serializable data class BossTemplate(val id: String, val name: String, val baseHp: Int)
@Serializable data class ChestTuning(
    val coinMin: Int, val coinMax: Int, val itemsPerChest: Int, val fragmentsPerBoss: Int,
    val collectionUnlockFragments: Int, val rarityWeights: Map<Rarity, Int>,
    val rarityStatMultipliers: Map<Rarity, Double>, val pityCommonStreak: Int,
    val duplicateDust: Map<Rarity, Int>,
)
@Serializable data class UpgradeTuning(val maxLevel: Int, val coinMultiplier: Int, val dustMultiplier: Int, val attackPerLevel: Int, val hpPerLevel: Int)
@Serializable data class EquipmentTemplate(val id: String, val slot: Slot, val name: String, val baseAttack: Int, val baseHp: Int)
@Serializable data class Balance(
    val schemaVersion: Int, val unit: String, val balanceVersion: String,
    val maxKillsPerSession: Int, val hero: HeroTuning, val sampling: SamplingTuning,
    val overspeed: OverspeedTuning, val session: SessionTuning,
    val bosses: List<BossTemplate>, val bossCycleHpMultiplier: Double,
    val chest: ChestTuning, val upgrades: UpgradeTuning, val equipment: List<EquipmentTemplate>,
) {
    init {
        require(bosses.isNotEmpty() && equipment.isNotEmpty())
        require(chest.rarityWeights.values.sum() == 100)
        require(bosses.map { it.id }.distinct().size == bosses.size)
        require(equipment.map { it.id }.distinct().size == equipment.size)
        require(hero.damageDivisor > 0 && maxKillsPerSession > 0)
    }
    fun template(id: String): EquipmentTemplate = equipment.single { it.id == id }
    fun stats(game: GameState): HeroStats {
        val items = game.equipped.values.mapNotNull { key -> game.equipment.find { it.key == key } }
        var attack = hero.baseAttack + (game.hero.level - 1) * hero.attackPerLevel
        var hp = hero.baseHp
        var reduction = 0.0
        for (item in items) {
            val t = template(item.templateId)
            val multiplier = chest.rarityStatMultipliers.getValue(item.rarity)
            attack += floor(t.baseAttack * multiplier).toInt()
            hp += floor(t.baseHp * multiplier).toInt()
            if (t.slot == Slot.ARMOR) {
                hp += item.upgradeLevel * upgrades.hpPerLevel
                reduction = overspeed.armorReductionByRarity.getValue(item.rarity)
            } else attack += item.upgradeLevel * upgrades.attackPerLevel
        }
        return HeroStats(attack, hp.toLong() * 1000, reduction.coerceAtMost(overspeed.maxArmorReductionFraction))
    }
    fun encounter(index: Int, cycle: Int, ids: IdSource): Encounter {
        val boss = bosses[index]
        val hp = ceil(boss.baseHp * bossCycleHpMultiplier.pow(cycle - 1)).coerceAtMost(1e12).toLong() * 1000
        return Encounter(ids.id(), boss.id, index, cycle, hp, hp)
    }
    companion object {
        val json = Json { encodeDefaults = true }
        fun parse(text: String): Balance = json.decodeFromString(text)
    }
}
