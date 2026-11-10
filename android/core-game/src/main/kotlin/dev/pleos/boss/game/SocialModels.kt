package dev.pleos.boss.game

import kotlinx.serialization.Serializable

@Serializable data class Person(val id: String, val code: String, val nickname: String)
@Serializable data class CabinetItem(val id: String, val item: Equipment, val locked: Boolean)
@Serializable data class Friend(val id: String, val code: String, val nickname: String, val items: List<CabinetItem>)
@Serializable data class FriendRequest(val id: String, val incoming: Boolean, val person: Person)
@Serializable data class Trade(val id: String, val incoming: Boolean, val status: String,
    val sender: Person, val recipient: Person, val give: Equipment, val want: Equipment, val expiresAt: Long)
@Serializable data class Delivery(val id: String, val item: Equipment)
@Serializable data class SocialSnapshot(val profile: Person, val friends: List<Friend>, val requests: List<FriendRequest>,
    val inventory: List<CabinetItem>, val trades: List<Trade>, val deliveries: List<Delivery>)
