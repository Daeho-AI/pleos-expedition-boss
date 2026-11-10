package dev.pleos.entry.boss.social

import android.content.Context
import android.util.Base64
import dev.pleos.boss.game.*
import dev.pleos.entry.boss.BossGraph
import dev.pleos.entry.boss.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.SecureRandom

data class SocialState(val server: String, val nickname: String, val profile: Person? = null,
    val snapshot: SocialSnapshot? = null, val error: String? = null)

class SocialController(private val graph: BossGraph, preferencesName: String = "friends-v1") {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val prefs = graph.context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(SocialState(
        prefs.getString("server", if (BuildConfig.IS_FIXTURE) "http://10.0.2.2:8765" else "") ?: "",
        prefs.getString("nickname", "") ?: "",
        prefs.getString("profile", null)?.let { runCatching { json.decodeFromString<Person>(it) }.getOrNull() },
    ))
    val state = mutable.asStateFlow()
    val busy = MutableStateFlow(false)
    private val realm: String get() = mutable.value.server + "#" + (mutable.value.profile?.id ?: "")

    private fun run(block: suspend () -> Unit) {
        if (graph.ux() != UxMode.PARKED_INTERACTIVE) {
            graph.messages.tryEmit("친구와 교환은 주차 후 이용할 수 있어요")
            return
        }
        if (!busy.compareAndSet(false, true)) return
        graph.scope.launch {
            try {
                mutable.value = mutable.value.copy(error = null)
                graph.repository.ready.await()
                block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.value = mutable.value.copy(error = when(e) {
                    is SocialError, is IllegalArgumentException -> e.message ?: "요청을 확인해 주세요"
                    else -> "서버에 연결하지 못했어요. 연결을 확인한 뒤 새로고침해 주세요. 전송 중인 장비는 보관됩니다."
                })
            } finally { busy.value = false }
        }
    }
    fun connect(server: String, nickname: String) = run {
        val endpoint = normalizeServer(server)
        require(nickname.trim().length in 1..20) { "닉네임은 1~20자로 입력해 주세요" }
        require(mutable.value.profile == null || mutable.value.server == endpoint) { "현재 프로필과 교환함은 연결한 서버에 보관됩니다" }
        val token = prefs.getString("token", null) ?: ByteArray(32).also { SecureRandom().nextBytes(it) }.let {
            Base64.encodeToString(it, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
        }
        check(prefs.edit().putString("server",endpoint).putString("nickname",nickname.trim()).putString("token",token).commit())
        mutable.value = mutable.value.copy(server = endpoint, nickname = nickname.trim())
        val profile = json.decodeFromString<Person>(request("/v1/profile", buildJsonObject { put("nickname", nickname.trim()) }))
        check(prefs.edit().putString("profile",json.encodeToString(profile)).commit())
        mutable.value = mutable.value.copy(profile = profile, nickname = profile.nickname)
        sync()
    }
    fun refresh() = run { if (mutable.value.profile != null) sync() }
    fun sendFriend(code: String) = command("/v1/friends/request", buildJsonObject { put("code",code.trim().uppercase()) })
    fun friendRequest(id: String, action: String) = command("/v1/requests/$id/$action")
    fun removeFriend(id: String) = command("/v1/friends/$id/remove")
    fun trade(id: String, action: String) = command("/v1/trades/$id/$action")
    fun offer(friend: String, give: String, want: String) = command("/v1/trades", buildJsonObject {
        put("id",graph.ids.id()); put("friendId",friend); put("giveId",give); put("wantId",want)
    })
    fun withdraw(id: String) = command("/v1/items/$id/withdraw")
    private fun command(path: String, body: JsonObject = buildJsonObject {}) = run {
        require(mutable.value.profile != null) { "먼저 친구 프로필을 연결해 주세요" }
        request(path,body)
        sync()
    }
    fun deposit(key: String) = run {
        require(mutable.value.profile != null) { "먼저 친구 프로필을 연결해 주세요" }
        val id = graph.ids.id()
        val result = graph.repository.change(id,"deposit:$key") {
            graph.economy.reserve(it,key,id,realm,graph.ux(),System.currentTimeMillis())
        }
        if (!result.success) throw SocialError(result.message)
        sync()
    }
    fun abortDeposit(id: String) = run {
        val pending = graph.repository.state.value.economy.deposits.find { it.id == id } ?: return@run
        require(pending.realm == realm) { "장비를 맡긴 서버에 다시 연결해 주세요" }
        val reply = json.parseToJsonElement(request("/v1/deposits/$id/abort", buildJsonObject {})).jsonObject
        graph.repository.change { graph.economy.finishDeposit(it,id,reply["status"]?.jsonPrimitive?.content == "CANCELLED") }
        sync()
    }
    private suspend fun sync() {
        // Persist the debit before sending; retry the same ID after timeouts/restarts.
        for (pending in graph.repository.state.value.economy.deposits.filter { it.realm == realm }) {
            if (graph.ux() != UxMode.PARKED_INTERACTIVE) break
            val reply = json.parseToJsonElement(request("/v1/deposits", buildJsonObject {
                put("id",pending.id); put("item",json.encodeToJsonElement(pending.item))
            })).jsonObject
            val status = reply["status"]?.jsonPrimitive?.content
            check(status == "COMMITTED" || status == "CANCELLED")
            graph.repository.change { graph.economy.finishDeposit(it,pending.id,status == "CANCELLED") }
        }
        var snapshot = json.decodeFromString<SocialSnapshot>(request("/v1/state"))
        var delivered = false
        for (delivery in snapshot.deliveries) {
            if (graph.ux() != UxMode.PARKED_INTERACTIVE) break
            // An unacknowledged server delivery may repeat. Room stores credit + receipt atomically.
            val result = graph.repository.change("delivery:$realm:${delivery.id}",json.encodeToString(delivery.item)) {
                graph.economy.receive(it,delivery.item)
            }
            if (!result.success) throw SocialError(result.message)
            request("/v1/deliveries/${delivery.id}/ack", buildJsonObject {})
            delivered = true
        }
        if (delivered) snapshot = json.decodeFromString(request("/v1/state"))
        mutable.value = mutable.value.copy(snapshot = snapshot, profile = snapshot.profile, error = null)
    }
    private fun request(path: String, body: JsonObject? = null): String {
        val connection = URL(mutable.value.server + path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10000; connection.readTimeout = 10000
            connection.instanceFollowRedirects = false; connection.useCaches = false
            connection.requestMethod = if (body == null) "GET" else "POST"
            connection.setRequestProperty("Authorization", "Bearer " + prefs.getString("token", ""))
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { input ->
                val buffer = ByteArray(4096)
                val output = java.io.ByteArrayOutputStream()
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    check(output.size() + n <= 2_000_000) { "서버 응답이 너무 커요" }
                    output.write(buffer,0,n)
                }
                output.toString("UTF-8")
            } ?: "{}"
            if (code !in 200..299) throw SocialError(runCatching {
                json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content
            }.getOrNull() ?: "서버 요청을 처리하지 못했어요 ($code)")
            return text
        } finally { connection.disconnect() }
    }
    private fun normalizeServer(value: String): String {
        val uri = runCatching { URI(value.trim().trimEnd('/')) }.getOrNull()
        require(uri != null && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.query == null && uri.fragment == null && uri.path.isNullOrEmpty() && (uri.port == -1 || uri.port in 1..65535)) { "서버 주소를 확인해 주세요" }
        val ip = uri.host.split('.').mapNotNull { it.toIntOrNull()?.takeIf { n -> n in 0..255 } }
        val local = uri.host == "localhost" || (ip.size == 4 && (ip[0] == 10 || ip[0] == 127 || (ip[0] == 192 && ip[1] == 168) || (ip[0] == 172 && ip[1] in 16..31)))
        require(uri.scheme == "https" || (BuildConfig.DEBUG && uri.scheme == "http" && local)) { "HTTPS 서버 주소가 필요해요. 개발용 앱에서는 로컬 서버도 연결할 수 있어요" }
        return uri.toASCIIString()
    }
    private class SocialError(message: String) : Exception(message)
}
