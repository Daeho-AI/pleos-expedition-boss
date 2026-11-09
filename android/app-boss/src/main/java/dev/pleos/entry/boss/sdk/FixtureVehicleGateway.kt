package dev.pleos.entry.boss.sdk

import android.os.SystemClock
import dev.pleos.boss.game.UxMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FixtureVehicleGateway : VehicleGateway {
    private val mutable = MutableStateFlow(GatewayState(
        source = "FIXTURE", naviConnection = "시연 데이터", carConnection = "시연 데이터",
        ux = UxMode.PARKED_INTERACTIVE, uxAtMs = SystemClock.elapsedRealtime(),
        speedPermission = true, powertrainPermission = true, setupAllowed = true,
    ))
    override val state = mutable.asStateFlow()
    override var onSignal: ((NavSignal) -> Unit)? = null
    override var activityVisible = true
    override fun start() = park()
    override fun stop() = Unit
    override fun refreshPermissions() = Unit
    fun drive(now: Long, speed: Double = 40.0, limit: Int? = 60) {
        mutable.update { it.copy(
            navStatus = "ROUTE_DRIVE", routeGuidance = true, routeAtMs = now,
            speedKph = speed, speedAtMs = now, limitKph = limit, limitAtMs = now,
            ux = UxMode.DRIVING_LIMITED, uxAtMs = now, setupAllowed = false,
            speedEvents = it.speedEvents + 1, limitEvents = it.limitEvents + 1, routeEvents = it.routeEvents + 1,
        ) }
    }
    fun park(now: Long = SystemClock.elapsedRealtime()) {
        mutable.update { it.copy(ux = UxMode.PARKED_INTERACTIVE, uxAtMs = now, setupAllowed = true,
            navStatus = "FREE_DRIVE", routeGuidance = false, routeAtMs = now, speedKph = 0.0, speedAtMs = now) }
    }
}
