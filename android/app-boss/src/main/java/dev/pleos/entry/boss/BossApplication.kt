package dev.pleos.entry.boss

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.room.Room
import dev.pleos.boss.game.*
import dev.pleos.entry.boss.data.GameDatabase
import dev.pleos.entry.boss.data.GameRepository
import dev.pleos.entry.boss.sdk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.util.UUID

class BossApplication : Application() {
    lateinit var graph: BossGraph
        private set
    override fun onCreate() { super.onCreate(); graph = BossGraph(this) }
}
class BossGraph(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val balance = Balance.parse(context.assets.open("balance.json").bufferedReader().use { it.readText() })
    private val random = SecureRandom()
    val ids = object : IdSource {
        override fun id() = UUID.randomUUID().toString()
        override fun seed() = random.nextLong()
    }
    val db = Room.databaseBuilder(context, GameDatabase::class.java, "expedition-v1.db").build()
    val repository = GameRepository(db, scope, balance, ids)
    private val engine = CombatEngine(balance, ids)
    private val actions = GameActions(balance)
    val gateway: VehicleGateway = if (BuildConfig.IS_FIXTURE) FixtureVehicleGateway() else PleosVehicleGateway(context, scope)
    val status = MutableStateFlow(CombatStatus())
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val fixtureRunning = MutableStateFlow(false)
    val actionBusy = MutableStateFlow(false)
    val fatalError = MutableStateFlow<String?>(null)
    @Volatile var activityVisible = false
    init {
        gateway.onSignal = { signal ->
            scope.launch {
                safe {
                    repository.change { game ->
                        ActionResult(if (signal == NavSignal.ARRIVED) engine.destinationArrived(game) else engine.destinationChanged(game), "", true)
                    }
                }
            }
        }
    }
    fun ux(): UxMode = if (BuildConfig.IS_FIXTURE) gateway.state.value.ux else gateway.state.value.input().ux
    fun visible(visible: Boolean) {
        activityVisible = visible
        gateway.activityVisible = visible
        scope.launch(Dispatchers.Main.immediate) {
            if (visible) {
                gateway.start()
                safe {
                    repository.ready.await()
                    if (repository.state.value.hero.autoExpeditionEnabled && !BuildConfig.IS_FIXTURE) startRecording()
                }
            } else if (!repository.state.value.hero.autoExpeditionEnabled) gateway.stop()
        }
    }
    private suspend fun safe(block: suspend () -> Unit) {
        try { block() }
        catch (t: Throwable) {
            if (t is CancellationException) throw t
            fatalError.value = "저장 또는 연결 오류: " + (t.message ?: t.javaClass.simpleName)
            status.value = CombatStatus(BattleState.DATA_PAUSED, "저장을 확인할 때까지 원정을 멈췄어요")
            messages.emit("기록을 저장하지 못했어요. 연결 화면을 확인해 주세요.")
        }
    }
    suspend fun tick() {
        if (fatalError.value != null) return
        safe {
            var nextStatus = status.value
            repository.change { game ->
                val result = engine.tick(game, gateway.state.value.input())
                nextStatus = result.status
                ActionResult(result.game, "", true)
            }
            status.value = nextStatus
        }
    }
    fun setEnabled(enabled: Boolean) {
        scope.launch {
            safe {
                if (enabled && ux() != UxMode.PARKED_INTERACTIVE) {
                    messages.emit("주차 상태를 확인한 뒤 원정을 켤 수 있어요")
                    return@safe
                }
                repository.change(ids.id(), "enabled:" + enabled) {
                    engine.resetContinuity()
                    ActionResult(it.copy(hero = it.hero.copy(autoExpeditionEnabled = enabled)), "", true)
                }
                withContext(Dispatchers.Main.immediate) {
                    if (enabled && !BuildConfig.IS_FIXTURE) startRecording()
                    else if (!enabled) context.stopService(Intent(context, ExpeditionService::class.java))
                }
                messages.emit(if (enabled) "자동 원정을 켰어요. 다음 여정부터 기록됩니다." else "자동 원정을 껐어요. 진행 상황은 보관됩니다.")
            }
        }
    }
    private fun startRecording() {
        try { ContextCompat.startForegroundService(context, Intent(context, ExpeditionService::class.java)) }
        catch (t: RuntimeException) { fatalError.value = "원정 기록 서비스 시작 실패: " + t.javaClass.simpleName }
    }
    private fun action(signature: String, transform: (GameState, UxMode) -> ActionResult) {
        if (!actionBusy.compareAndSet(false, true)) return
        val actionId = ids.id()
        scope.launch {
            try {
                safe {
                    val result = repository.change(actionId, signature) { transform(it, ux()) }
                    messages.emit(result.message)
                }
            } finally { actionBusy.value = false }
        }
    }
    fun openChest(id: String) = action("open:" + id) { game, ux -> actions.openChest(game, id, System.currentTimeMillis(), ux) }
    fun openAll() = action("open-all") { game, ux ->
        if (ux != UxMode.PARKED_INTERACTIVE) ActionResult(game, "주차 후 열 수 있어요", false)
        else {
            var next = game
            for (chest in game.unopened) next = actions.openChest(next, chest.id, System.currentTimeMillis(), ux).game
            ActionResult(next, game.unopened.size.toString() + "개의 상자 보상을 받았어요", true)
        }
    }
    fun equip(key: String) = action("equip:" + key) { game, ux -> actions.equip(game, key, ux) }
    fun upgrade(key: String) = action("upgrade:" + key) { game, ux -> actions.upgrade(game, key, ux) }
    fun reconnect() {
        scope.launch(Dispatchers.Main.immediate) { gateway.stop(); gateway.start() }
    }
    fun simulateTrip(seconds: Int = 420, speed: Double = 40.0, limit: Int? = 60) {
        if (!BuildConfig.IS_FIXTURE || !fixtureRunning.compareAndSet(false, true)) return
        val fixture = gateway as FixtureVehicleGateway
        scope.launch {
            try {
                safe {
                    repository.ready.await()
                    var virtualNow = SystemClock.elapsedRealtime()
                    var virtualWall = maxOf(System.currentTimeMillis(), (repository.state.value.lastFinishedSession?.endedAt ?: 0) + 60_000)
                    repository.change { game ->
                        engine.resetContinuity()
                        ActionResult(game.copy(hero = game.hero.copy(autoExpeditionEnabled = true)), "", true)
                    }
                    for (i in 0..seconds) {
                        fixture.drive(virtualNow, speed, limit)
                        var nextStatus = status.value
                        repository.change { game ->
                            val result = engine.tick(game, fixture.state.value.input(virtualNow, virtualWall))
                            nextStatus = result.status
                            ActionResult(result.game, "", true)
                        }
                        status.value = nextStatus
                        virtualNow += 1000; virtualWall += 1000
                        if (i % 10 == 0) delay(15)
                    }
                    repository.change { ActionResult(engine.destinationArrived(it), "", true) }
                    repeat(31) {
                        fixture.park(virtualNow)
                        repository.change { game ->
                            val result = engine.tick(game, fixture.state.value.input(virtualNow, virtualWall))
                            status.value = result.status
                            ActionResult(result.game, "", true)
                        }
                        virtualNow += 1000; virtualWall += 1000
                    }
                    messages.emit("시연 운행이 끝났어요. 상자와 이어갈 보스를 확인하세요.")
                }
            } finally {
                fixture.park()
                fixtureRunning.value = false
            }
        }
    }
    fun exportEvidence() {
        scope.launch {
            val source = gateway.state.value
            val report = JSONObject().apply {
                put("mode", source.source); put("naviHelperVersion", "3.0.5")
                put("deviceFingerprint", android.os.Build.FINGERPRINT)
                put("deviceModel", android.os.Build.MODEL)
                put("recordedAtEpochMs", System.currentTimeMillis())
                put("naviConnection", source.naviConnection); put("carConnection", source.carConnection)
                put("navStatus", source.navStatus); put("routeGuidance", source.routeGuidance)
                put("naviMode", source.naviMode); put("rawIsRouting", source.rawIsRouting ?: JSONObject.NULL)
                put("speedPermission", source.speedPermission); put("powertrainPermission", source.powertrainPermission)
                put("speedEvents", source.speedEvents); put("limitEvents", source.limitEvents); put("routeEvents", source.routeEvents)
                put("movingSpeedEvents", source.movingSpeedEvents); put("maxObservedSpeedKph", source.maxObservedSpeedKph)
                put("receivedWhileBackground", source.receivedWhileBackground); put("ux", ux().name)
                put("speedKph", source.speedKph ?: JSONObject.NULL); put("limitKph", source.limitKph ?: JSONObject.NULL)
                put("lastError", source.lastError ?: JSONObject.NULL); put("fatalError", fatalError.value ?: JSONObject.NULL)
                put("automaticExpeditionEnabled", repository.state.value.hero.autoExpeditionEnabled)
                put("bossHpMilli", repository.state.value.encounter?.hpMilli ?: JSONObject.NULL)
                put("sessionCount", repository.state.value.sessions.size)
                put("chestCount", repository.state.value.chests.size)
                put("note", "Observed counts only. Fixture values do not prove SDK integration.")
            }
            File(context.filesDir, "sdk-evidence.json").writeText(report.toString(2))
            messages.emit("연결 확인 기록을 기기에 저장했어요")
        }
    }
}
