package dev.pleos.entry.boss.sdk

import android.os.SystemClock
import dev.pleos.boss.game.DrivingInput
import dev.pleos.boss.game.UxMode
import kotlinx.coroutines.flow.StateFlow

enum class NavSignal { ARRIVED, DESTINATION_CHANGED }
data class GatewayState(
    val source: String,
    val naviConnection: String = "연결 전",
    val carConnection: String = "연결 전",
    val navStatus: String = "UNKNOWN",
    val naviMode: String = "UNKNOWN",
    val rawIsRouting: Boolean? = null,
    val routeGuidance: Boolean = false,
    val routeAtMs: Long? = null,
    val speedKph: Double? = null,
    val speedAtMs: Long? = null,
    val limitKph: Int? = null,
    val limitAtMs: Long? = null,
    val ux: UxMode = UxMode.UNKNOWN,
    val uxAtMs: Long? = null,
    val speedPermission: Boolean = false,
    val powertrainPermission: Boolean = false,
    val setupAllowed: Boolean = false,
    val speedEvents: Long = 0,
    val movingSpeedEvents: Long = 0,
    val maxObservedSpeedKph: Double = 0.0,
    val limitEvents: Long = 0,
    val routeEvents: Long = 0,
    val receivedWhileBackground: Long = 0,
    val lastError: String? = null,
) {
    fun input(now: Long = SystemClock.elapsedRealtime(), wall: Long = System.currentTimeMillis()): DrivingInput =
        DrivingInput(now, wall, speedKph, speedAtMs, limitKph, limitAtMs,
            routeGuidance, routeAtMs,
            if (uxAtMs != null && now - uxAtMs in 0..2500) ux else UxMode.UNKNOWN)
}
interface VehicleGateway {
    val state: StateFlow<GatewayState>
    var onSignal: ((NavSignal) -> Unit)?
    var activityVisible: Boolean
    fun start()
    fun stop()
    fun refreshPermissions()
}
