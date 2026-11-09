package dev.pleos.entry.boss.sdk

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.annotation.SuppressLint
import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import dev.pleos.boss.game.UxMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Public, read-only SDK integration. No privileged APIs, navigation writes or VHAL writes. */
@SuppressLint("MissingPermission")
class PleosVehicleGateway(private val context: Context, private val scope: CoroutineScope) : VehicleGateway {
    private val mutable = MutableStateFlow(GatewayState(source = "PLEOS_SDK"))
    override val state = mutable.asStateFlow()
    override var onSignal: ((NavSignal) -> Unit)? = null
    override var activityVisible: Boolean = true
    private var navi: NaviHelper? = null
    private var car: Car? = null
    private var properties: CarPropertyManager? = null
    private var restrictions: CarUxRestrictionsManager? = null
    private var poll: Job? = null
    private var started = false
    private var speedSubscribed = false
    private var gear: Int? = null
    private fun now() = SystemClock.elapsedRealtime()
    private fun permission(name: String) = context.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED
    private fun error(label: String, t: Throwable) {
        Log.w("BossSDK", label + ": " + t.javaClass.simpleName + " " + t.message)
        mutable.update { it.copy(lastError = label + ": " + t.javaClass.simpleName + " " + (t.message ?: "")) }
    }
    private fun backgroundIncrement() = if (activityVisible) 0L else 1L
    private val listener = object : NaviHelperEventListener {
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
            // Use the explicit guidance mode. On Connect 2.1.0 isRouting can remain
            // false during active native guidance; retain it as diagnostic evidence.
            mutable.update { it.copy(naviMode = routeStateInfo.naviMode.name, rawIsRouting = routeStateInfo.isRouting,
                routeGuidance = routeStateInfo.naviMode == NaviMode.ROUTE_GUIDING,
                routeAtMs = now(), routeEvents = it.routeEvents + 1,
                receivedWhileBackground = it.receivedWhileBackground + backgroundIncrement()) }
        }
        override fun onNavigationStatus(status: NavigationStatus) {
            Log.i("BossSDK", "NavigationStatus=" + status.name)
            // Transition events are not guaranteed to replay after a process restart.
            // Fresh RouteStateInfo.naviMode is the authoritative current mode.
            // A non-driving transition still invalidates the previous guidance immediately.
            mutable.update { it.copy(navStatus = status.name,
                routeGuidance = it.routeGuidance && status == NavigationStatus.ROUTE_DRIVE) }
        }
        override fun onCurrentRoadSpeedLimit(speedLimit: Int) {
            mutable.update { it.copy(limitKph = speedLimit.takeIf { limit -> limit > 0 }, limitAtMs = now(), limitEvents = it.limitEvents + 1, receivedWhileBackground = it.receivedWhileBackground + backgroundIncrement()) }
        }
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            onSignal?.invoke(NavSignal.ARRIVED)
        }
        override fun onDestinationChanged() { onSignal?.invoke(NavSignal.DESTINATION_CHANGED) }
        override fun onRouteCancelled() { mutable.update { it.copy(routeGuidance = false, routeAtMs = now()) } }
        override fun onError(errorCode: NaviErrorCode) { mutable.update { it.copy(lastError = "내비: " + errorCode.name) } }
    }
    private val speedCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            if (value.propertyId != VehiclePropertyIds.PERF_VEHICLE_SPEED) return
            if (!permission(Car.PERMISSION_SPEED) || value.status != CarPropertyValue.STATUS_AVAILABLE) {
                mutable.update { it.copy(speedKph = null, speedAtMs = null) }
                return
            }
            val speed = (value.value as? Float)?.toDouble()?.times(3.6)?.takeIf { it.isFinite() }
            val timestamp = (value.timestamp / 1_000_000).takeIf { it > 0 }
            mutable.update { it.copy(speedKph = speed, speedAtMs = timestamp,
                speedEvents = it.speedEvents + 1,
                movingSpeedEvents = it.movingSpeedEvents + if ((speed ?: 0.0) >= 5.0) 1L else 0L,
                maxObservedSpeedKph = maxOf(it.maxObservedSpeedKph, speed ?: 0.0),
                receivedWhileBackground = it.receivedWhileBackground + backgroundIncrement()) }
        }
        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            mutable.update { it.copy(speedKph = null, speedAtMs = null, lastError = "차량 속도 표본 오류") }
        }
    }
    override fun start() {
        if (started) return
        started = true
        mutable.update { it.copy(naviConnection = "연결 중", carConnection = "연결 중") }
        try {
            navi = NaviHelper(context).also { helper ->
                helper.addListener(listener)
                helper.initialize(
                    onConnected = {
                        mutable.update { it.copy(naviConnection = "CONNECTED") }
                        Log.i("BossSDK", "NaviHelper 3.0.5 connected")
                        runCatching { helper.getRouteStateInfo() }.onFailure { error("내비 상태 조회", it) }
                    },
                    onFailed = { t ->
                        mutable.update { it.copy(naviConnection = "FAILED", routeGuidance = false, routeAtMs = null, limitKph = null, limitAtMs = null) }
                        error("내비 연결", t)
                    },
                )
            }
        } catch (t: Throwable) { mutable.update { it.copy(naviConnection = "UNAVAILABLE") }; error("내비 초기화", t) }
        try {
            car = Car.createCar(context, Handler(Looper.getMainLooper()), Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                if (!started) return@createCar
                if (!ready) {
                    properties = null; restrictions = null; speedSubscribed = false; gear = null
                    mutable.update { it.copy(carConnection = "DISCONNECTED", ux = UxMode.UNKNOWN, uxAtMs = null, speedKph = null, speedAtMs = null, setupAllowed = false) }
                } else {
                    properties = connected.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                    restrictions = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
                    mutable.update { it.copy(carConnection = "CONNECTED") }
                    Log.i("BossSDK", "Car service connected")
                    runCatching { restrictions?.registerListener { readUx() } }.onFailure { error("UX 구독", it) }
                    refreshPermissions()
                    readUx()
                }
            }
        } catch (t: Throwable) { mutable.update { it.copy(carConnection = "UNAVAILABLE") }; error("차량 서비스", t) }
        poll = scope.launch(Dispatchers.Main.immediate) {
            var loops = 0
            while (isActive && started) {
                refreshPermissions()
                readUx()
                if (loops++ % 5 == 0 && mutable.value.naviConnection == "CONNECTED") {
                    runCatching { navi?.getRouteStateInfo() }.onFailure { error("내비 상태 조회", it) }
                }
                delay(1000)
            }
        }
    }
    override fun refreshPermissions() {
        val speed = permission(Car.PERMISSION_SPEED)
        val powertrain = permission(Car.PERMISSION_POWERTRAIN)
        mutable.update { it.copy(speedPermission = speed, powertrainPermission = powertrain) }
        val manager = properties ?: return
        if (!speed && speedSubscribed) {
            runCatching { manager.unregisterCallback(speedCallback) }
            speedSubscribed = false
            mutable.update { it.copy(speedKph = null, speedAtMs = null) }
        }
        if (speed && !speedSubscribed) {
            try {
                val config = manager.getCarPropertyConfig(VehiclePropertyIds.PERF_VEHICLE_SPEED)
                if (config == null) {
                    mutable.update { it.copy(lastError = "PERF_VEHICLE_SPEED 미지원") }
                } else {
                    speedSubscribed = manager.registerCallback(speedCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
                    if (!speedSubscribed) mutable.update { it.copy(lastError = "차량 속도 구독이 거부됐어요") }
                }
            } catch (t: Throwable) { error("속도 구독", t) }
        }
        if (!powertrain) gear = null
    }
    private fun readUx() {
        try {
            val ux = restrictions?.currentCarUxRestrictions
            val setupAllowed = ux != null && !ux.isRequiresDistractionOptimization &&
                (ux.activeRestrictions and android.car.drivingstate.CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP) == 0
            gear = if (permission(Car.PERMISSION_POWERTRAIN)) {
                val value = properties?.getProperty<Int>(VehiclePropertyIds.GEAR_SELECTION, 0)
                value?.value?.takeIf { value.status == CarPropertyValue.STATUS_AVAILABLE }
            } else null
            val mode = when {
                ux == null || gear == null -> UxMode.UNKNOWN
                setupAllowed && gear == VehicleGear.GEAR_PARK -> UxMode.PARKED_INTERACTIVE
                else -> UxMode.DRIVING_LIMITED
            }
            mutable.update { it.copy(ux = mode, uxAtMs = now(), setupAllowed = setupAllowed) }
        } catch (t: Throwable) {
            mutable.update { it.copy(ux = UxMode.UNKNOWN, uxAtMs = null, setupAllowed = false) }
            error("주차 상태", t)
        }
    }
    override fun stop() {
        if (!started) return
        started = false; poll?.cancel(); poll = null
        runCatching { navi?.removeListener(listener); navi?.release() }; navi = null
        runCatching { properties?.unregisterCallback(speedCallback) }
        runCatching { restrictions?.unregisterListener() }
        runCatching { car?.disconnect() }
        car = null; properties = null; restrictions = null; speedSubscribed = false; gear = null
        mutable.update { it.copy(naviConnection = "STOPPED", carConnection = "STOPPED", navStatus = "UNKNOWN",
            naviMode = "UNKNOWN", rawIsRouting = null,
            routeGuidance = false, routeAtMs = null, speedKph = null, speedAtMs = null,
            limitKph = null, limitAtMs = null, ux = UxMode.UNKNOWN, uxAtMs = null, setupAllowed = false) }
    }
}
