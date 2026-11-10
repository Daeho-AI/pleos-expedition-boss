package dev.pleos.boss.game

import kotlin.random.Random

class RewardGenerator(private val balance: Balance) {
    fun create(encounter: Encounter, commonStreak: Int, ids: IdSource): Chest {
        val seed = ids.seed()
        val random = Random(seed)
        val draw = random.nextInt(100)
        var threshold = 0
        var rarity = Rarity.COMMON
        for (option in Rarity.values()) {
            threshold += balance.chest.rarityWeights.getValue(option)
            if (draw < threshold) { rarity = option; break }
        }
        if (commonStreak >= balance.chest.pityCommonStreak && rarity == Rarity.COMMON) rarity = Rarity.RARE
        return Chest(
            id = ids.id(), encounterId = encounter.id, bossId = encounter.bossId, seed = seed,
            balanceVersion = balance.balanceVersion,
            coins = random.nextInt(balance.chest.coinMin, balance.chest.coinMax + 1),
            templateId = balance.equipment[random.nextInt(balance.equipment.size)].id, rarity = rarity,
        )
    }
    fun addExperience(hero: Hero): Hero {
        var level = hero.level
        var xp = hero.xp + balance.hero.xpPerBoss
        while (level < balance.hero.maxLevel && xp >= balance.hero.xpToNextLevelMultiplier * level) {
            xp -= balance.hero.xpToNextLevelMultiplier * level
            level++
        }
        if (level == balance.hero.maxLevel) xp = 0
        return hero.copy(level = level, xp = xp)
    }
}

data class ActionResult(val game: GameState, val message: String, val success: Boolean)
class GameActions(private val balance: Balance) {
    fun openChest(game: GameState, id: String, now: Long, ux: UxMode): ActionResult {
        if (ux != UxMode.PARKED_INTERACTIVE) return ActionResult(game, "주차 후 열 수 있어요", false)
        val chest = game.chests.find { it.id == id } ?: return ActionResult(game, "상자를 찾을 수 없어요", false)
        if (chest.openedAt != null) return ActionResult(game, "이미 받은 보상이에요", true)
        val item = Equipment(chest.templateId, chest.rarity)
        val duplicate = game.equipment.any { it.key == item.key }
        val dust = if (duplicate) balance.chest.duplicateDust.getValue(item.rarity) else 0
        val updated = game.copy(
            hero = game.hero.copy(coins = game.hero.coins + chest.coins, dust = game.hero.dust + dust),
            chests = game.chests.map { if (it.id == id) it.copy(openedAt = now, convertedDust = dust) else it },
            equipment = if (duplicate) game.equipment else game.equipment + item,
            collection = game.collection + (chest.bossId to ((game.collection[chest.bossId] ?: 0) + balance.chest.fragmentsPerBoss)),
        )
        return ActionResult(updated, if (duplicate) "중복 장비가 강화 가루 " + dust + "개로 바뀌었어요" else balance.template(item.templateId).name + " 획득", true)
    }
    fun equip(game: GameState, key: String, ux: UxMode): ActionResult {
        if (ux != UxMode.PARKED_INTERACTIVE) return ActionResult(game, "주차 후 장착할 수 있어요", false)
        val item = game.equipment.find { it.key == key } ?: return ActionResult(game, "보유하지 않은 장비예요", false)
        var updated = game.copy(equipped = game.equipped + (balance.template(item.templateId).slot to key))
        updated.activeSession?.let { updated = updated.withSession(it.copy(hpMilli = it.hpMilli.coerceAtMost(balance.stats(updated).maxHpMilli))) }
        return ActionResult(updated, balance.template(item.templateId).name + " 장착", true)
    }
    fun upgrade(game: GameState, key: String, ux: UxMode): ActionResult {
        if (ux != UxMode.PARKED_INTERACTIVE) return ActionResult(game, "주차 후 강화할 수 있어요", false)
        val item = game.equipment.find { it.key == key } ?: return ActionResult(game, "장비를 찾을 수 없어요", false)
        if (item.upgradeLevel >= balance.upgrades.maxLevel) return ActionResult(game, "최대 강화에 도달했어요", false)
        val next = item.upgradeLevel + 1
        val coins = next * balance.upgrades.coinMultiplier
        val dust = next * balance.upgrades.dustMultiplier
        if (game.hero.coins < coins || game.hero.dust < dust) return ActionResult(game, "코인 또는 강화 가루가 부족해요", false)
        return ActionResult(
            game.copy(
                hero = game.hero.copy(coins = game.hero.coins - coins, dust = game.hero.dust - dust),
                equipment = game.equipment.map { if (it.key == key) it.copy(upgradeLevel = next) else it },
            ), "+" + next + " 강화 완료", true,
        )
    }
    fun unequip(game: GameState, key: String, ux: UxMode): ActionResult {
        if (ux != UxMode.PARKED_INTERACTIVE) return ActionResult(game, "주차 후 해제할 수 있어요", false)
        var next = game.copy(equipped = game.equipped.filterValues { it != key })
        next.activeSession?.let { next = next.withSession(it.copy(hpMilli = it.hpMilli.coerceAtMost(balance.stats(next).maxHpMilli))) }
        return ActionResult(next, "장착을 해제했어요", true)
    }
}
