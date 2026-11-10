package dev.pleos.boss.game

import kotlinx.serialization.Serializable

@Serializable data class PendingDeposit(val id: String, val realm: String, val item: Equipment, val createdAt: Long)
@Serializable data class EconomyEvent(val kind: String, val label: String, val coins: Int, val at: Long)
@Serializable data class EconomyState(
    val deposits: List<PendingDeposit> = emptyList(),
    val history: List<EconomyEvent> = emptyList(),
)
data class ShopProduct(val id: String, val name: String, val description: String, val price: Int,
    val level: Int = 1, val equipment: Equipment? = null, val dust: Int = 0, val bossId: String? = null)

class EconomyActions(private val balance: Balance) {
    val catalog: List<ShopProduct> = listOf(ShopProduct("dust", "강화 가루 꾸러미", "강화 가루 5개", 45, dust = 5)) +
        balance.equipment.flatMap { t -> listOf(
            ShopProduct("gear:${t.id}:COMMON", t.name, "일반 · 확정 장비", 60, equipment = Equipment(t.id, Rarity.COMMON)),
            ShopProduct("gear:${t.id}:RARE", t.name, "희귀 · 확정 장비", 180, 3, Equipment(t.id, Rarity.RARE)),
        ) } + balance.bosses.map { b -> ShopProduct("fragment:${b.id}", b.name + "의 조각", "도감 조각 1개 · 해금 전까지", 70, bossId = b.id) }

    private fun fail(game: GameState, message: String) = ActionResult(game, message, false)
    private fun history(game: GameState, kind: String, label: String, coins: Int, at: Long) =
        game.copy(economy = game.economy.copy(history = (game.economy.history + EconomyEvent(kind, label, coins, at)).takeLast(50)))

    fun buy(game: GameState, productId: String, ux: UxMode, at: Long): ActionResult {
        if (ux != UxMode.PARKED_INTERACTIVE) return fail(game, "주차 후 구매할 수 있어요")
        val product = catalog.find { it.id == productId } ?: return fail(game, "판매하지 않는 상품이에요")
        if (game.hero.level < product.level) return fail(game, "레벨 ${product.level}부터 구매할 수 있어요")
        if (game.hero.coins < product.price) return fail(game, "코인이 부족해요")
        val item = product.equipment
        if (item != null && (game.equipment.any { it.key == item.key } || game.economy.deposits.any { it.item.key == item.key }))
            return fail(game, "이미 보유 중이거나 교환함으로 보내는 장비예요")
        val boss = product.bossId
        if (boss != null && (game.collection[boss] ?: 0) >= balance.chest.collectionUnlockFragments)
            return fail(game, "이미 해금한 도감이에요")
        val next = game.copy(
            hero = game.hero.copy(coins = game.hero.coins - product.price, dust = game.hero.dust + product.dust),
            equipment = if (item == null) game.equipment else game.equipment + item,
            collection = if (boss == null) game.collection else game.collection + (boss to ((game.collection[boss] ?: 0) + 1)),
        )
        return ActionResult(history(next, "BUY", product.name, -product.price, at), product.name + " 구매 완료", true)
    }
    fun salePrice(item: Equipment): Int = when(item.rarity) {
        Rarity.COMMON -> 15; Rarity.RARE -> 45; Rarity.EPIC -> 100; Rarity.LEGENDARY -> 200
    } + item.upgradeLevel * 8
    fun sell(game: GameState, key: String, ux: UxMode, at: Long): ActionResult {
        if (ux != UxMode.PARKED_INTERACTIVE) return fail(game, "주차 후 판매할 수 있어요")
        if (key in game.equipped.values) return fail(game, "장착 중인 장비는 판매할 수 없어요")
        val item = game.equipment.find { it.key == key } ?: return fail(game, "보유하지 않은 장비예요")
        val price = salePrice(item)
        val next = game.copy(hero = game.hero.copy(coins = game.hero.coins + price), equipment = game.equipment.filterNot { it.key == key })
        return ActionResult(history(next, "SELL", balance.template(item.templateId).name, price, at), "판매 완료 · 코인 +$price", true)
    }
    fun reserve(game: GameState, key: String, id: String, realm: String, ux: UxMode, at: Long): ActionResult {
        if (ux != UxMode.PARKED_INTERACTIVE) return fail(game, "주차 후 교환함에 맡길 수 있어요")
        if (key in game.equipped.values) return fail(game, "장착 중인 장비는 맡길 수 없어요")
        if (game.economy.deposits.any { it.id == id }) return fail(game, "이미 처리 중인 전송이에요")
        val item = game.equipment.find { it.key == key } ?: return fail(game, "보유하지 않은 장비예요")
        return ActionResult(game.copy(equipment = game.equipment.filterNot { it.key == key },
            economy = game.economy.copy(deposits = game.economy.deposits + PendingDeposit(id, realm, item, at))), "교환함으로 보내는 중이에요", true)
    }
    fun finishDeposit(game: GameState, id: String, returned: Boolean): ActionResult {
        val pending = game.economy.deposits.find { it.id == id } ?: return ActionResult(game, "이미 처리했어요", true)
        val next = game.copy(economy = game.economy.copy(deposits = game.economy.deposits.filterNot { it.id == id }))
        return if (returned) receive(next, pending.item) else ActionResult(next, "교환함에 맡겼어요", true)
    }
    fun receive(game: GameState, item: Equipment): ActionResult {
        if (balance.equipment.none { it.id == item.templateId } || item.upgradeLevel !in 0..balance.upgrades.maxLevel)
            return fail(game, "받을 수 없는 장비 정보예요")
        val existing = game.equipment.find { it.key == item.key }
        val next = if (existing == null) game.copy(equipment = game.equipment + item) else game.copy(
            equipment = game.equipment.map { if (it.key == item.key) it.copy(upgradeLevel = maxOf(it.upgradeLevel, item.upgradeLevel)) else it },
            hero = game.hero.copy(dust = game.hero.dust + balance.chest.duplicateDust.getValue(item.rarity)),
        )
        return ActionResult(next, if (existing == null) "장비를 받았어요" else "높은 강화는 유지하고 중복 장비는 가루로 바꿨어요", true)
    }
}
