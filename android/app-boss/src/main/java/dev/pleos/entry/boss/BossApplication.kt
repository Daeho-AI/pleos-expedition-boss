package dev.pleos.entry.boss

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
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
    val db = Room.databaseBuilder(context, GameDatabase::class.java, "expedition-v1.db").addMigrations(GameDatabase.MIGRATION_1_2).build()
    val repository = GameRepository(db, scope, balance, ids)
    private val engine = IdleEngine(balance, ids)
    private val actions = GameActions(balance)
    val economy = EconomyActions(balance)
    val social by lazy { dev.pleos.entry.boss.social.SocialController(this) }
    val gateway: VehicleGateway = if (BuildConfig.IS_FIXTURE) FixtureVehicleGateway() else PleosVehicleGateway(context, scope)
    val status = MutableStateFlow(CombatStatus())
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val fixtureRunning = MutableStateFlow(false)
    val actionBusy = MutableStateFlow(false)
    val fatalError = MutableStateFlow<String?>(null)
    val foreground = MutableStateFlow(false)
    val combatLoopActive = MutableStateFlow(false)
    @Volatile var activityVisible = false
    init {
        scope.launch(Dispatchers.Main.immediate) {
            safe {
                repository.ready.await()
                // A new process must perform a return settlement, even after an abrupt kill.
                repository.change { ActionResult(engine.pause(it, System.currentTimeMillis()), "", true) }
                combine(foreground, gateway.state,
                    repository.state.map { it.hero.autoExpeditionEnabled }.distinctUntilChanged(),
                    fixtureRunning, fatalError) { visible, source, enabled, simulation, error ->
                    visible && enabled && !simulation && error == null &&
                        (if (BuildConfig.IS_FIXTURE) source.ux else source.input().ux) == UxMode.PARKED_INTERACTIVE
                }.distinctUntilChanged().collectLatest { playable ->
                    if (playable) {
                        combatLoopActive.value = true
                        try {
                            while (currentCoroutineContext().isActive && fatalError.value == null) {
                                withContext(Dispatchers.IO) { tick() }
                                delay(1_000)
                            }
                        } finally { combatLoopActive.value = false }
                    } else {
                        repository.change { ActionResult(engine.pause(it, System.currentTimeMillis()), "", true) }
                        status.value = CombatStatus(BattleState.DATA_PAUSED, "주차 후 게임으로 돌아오면 보상을 정산합니다")
                    }
                }
            }
        }
    }
    fun ux(): UxMode = if (BuildConfig.IS_FIXTURE) gateway.state.value.ux else gateway.state.value.input().ux
    fun visible(visible: Boolean) {
        activityVisible = visible
        foreground.value = visible
        gateway.activityVisible = visible
        if (visible) gateway.start() else gateway.stop()
    }
    private fun clock() = ProgressClock(System.currentTimeMillis(), SystemClock.elapsedRealtime(),
        runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrNull())
    private fun progressInput(): ProgressInput {
        val clock = clock()
        return ProgressInput(clock, ux(), activityVisible, gateway.state.value.distance(clock.elapsedMs))
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
                val result = engine.tick(game, progressInput())
                nextStatus = result.status
                ActionResult(result.game, "", true)
            }
            status.value = nextStatus
        }
    }
    fun setEnabled(enabled: Boolean) {
        scope.launch {
            safe {
                if (!activityVisible || ux() != UxMode.PARKED_INTERACTIVE) {
                    messages.emit("주차 상태에서 자동 사냥을 변경할 수 있어요")
                    return@safe
                }
                repository.change(ids.id(), "enabled:" + enabled) {
                    ActionResult(engine.setEnabled(it, enabled, progressInput()), "", true)
                }
                messages.emit(if (enabled) "자동 사냥을 시작했어요. 주행 중에는 멈추고 복귀할 때 정산합니다." else "자동 사냥을 껐어요. 진행 상황은 보관됩니다.")
            }
        }
    }
    private fun action(signature: String, transform: (GameState, UxMode) -> ActionResult) {
        if (!actionBusy.compareAndSet(false, true)) return
        val actionId = ids.id()
        scope.launch {
            try {
                safe {
                    val result = repository.change(actionId, signature) {
                        transform(it, if (activityVisible) ux() else UxMode.UNKNOWN)
                    }
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
    fun unequip(key: String) = action("unequip:" + key) { game, ux -> actions.unequip(game, key, ux) }
    fun upgrade(key: String) = action("upgrade:" + key) { game, ux -> actions.upgrade(game, key, ux) }
    fun buy(product: String) = action("buy:" + product) { game, ux -> economy.buy(game, product, ux, System.currentTimeMillis()) }
    fun sell(key: String) = action("sell:" + key) { game, ux -> economy.sell(game, key, ux, System.currentTimeMillis()) }
    fun reconnect() {
        scope.launch(Dispatchers.Main.immediate) { gateway.stop(); if (activityVisible) gateway.start() }
    }
    fun simulateTrip(seconds: Int = 420, distanceKm: Double = 10.0) {
        if (!BuildConfig.IS_FIXTURE || !activityVisible || ux() != UxMode.PARKED_INTERACTIVE ||
            !fixtureRunning.compareAndSet(false, true)) return
        val fixture = gateway as FixtureVehicleGateway
        scope.launch {
            try {
                safe {
                    repository.ready.await()
                    val now = clock()
                    val duration = seconds.coerceIn(0, 28_800) * 1_000L
                    val meters = fixture.state.value.odometerMeters ?: 100_000L
                    repository.change { game ->
                        val paused = engine.pause(game, now.wallMs)
                        ActionResult(paused.copy(hero = paused.hero.copy(autoExpeditionEnabled = true,
                            progress = paused.hero.progress.copy(
                                accountedWallMs = now.wallMs - duration, accountedElapsedMs = now.elapsedMs - duration,
                                bootId = now.bootId, paused = true, awaitingDistance = true,
                                distanceBaselineMeters = meters, distanceSource = "FIXTURE_ODOMETER_KM",
                                departureAttack = balance.stats(game).attack,
                            ))), "", true)
                    }
                    fixture.drive(SystemClock.elapsedRealtime())
                    delay(1_500) // No combat calculation runs while the simulated car is driving.
                    fixture.park(distanceMeters = meters + (distanceKm.coerceIn(0.0, 100.0) * 1_000).toLong())
                    tick()
                    messages.emit("복귀 정산과 필살의 일격이 적용됐어요. 주차 중 자동 사냥이 이어집니다.")
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
                put("vehicleSdkVersion", "2.3.2")
                put("odometerStatus", source.odometerStatus)
                put("odometerMeters", source.odometerMeters ?: JSONObject.NULL)
                put("combatLoopActive", combatLoopActive.value)
                put("gamePaused", repository.state.value.hero.progress.paused)
                put("note", "Observed counts only. Fixture values do not prove SDK integration.")
            }
            File(context.filesDir, "sdk-evidence.json").writeText(report.toString(2))
            messages.emit("연결 확인 기록을 기기에 저장했어요")
        }
    }
}
