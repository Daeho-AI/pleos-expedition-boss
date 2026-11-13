package dev.pleos.entry.boss.sdk

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.roundToLong

/** One-shot public odometer reads while parked. No driving listener or background tracking. */
class ParkedOdometerClient(
    private val context: Context,
    private val allowed: () -> Boolean,
    private val publish: (Long?, Long?, String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    @Volatile private var generation = 0
    private var requestedAt = -10_000L

    fun refresh() {
        if (!allowed()) return
        val now = SystemClock.elapsedRealtime()
        if (now - requestedAt < 3_000) return
        requestedAt = now
        val request = ++generation
        fun deliver(meters: Long?, status: String) {
            main.post {
                if (request == generation && allowed() && SystemClock.elapsedRealtime() - now <= 5_000)
                    publish(meters, if (meters != null) SystemClock.elapsedRealtime() else null, status)
            }
        }
        try {
            val sdk = vehicle ?: Vehicle(context.applicationContext).also { it.initialize(); vehicle = it }
            val odometer = sdk.getOdometer()
            odometer.checkCurrentDistanceDrivenCapabilities(
                onComplete = { config ->
                    if (request == generation && allowed()) {
                        if (config.accessType != AccessType.READ && config.accessType != AccessType.READ_WRITE) {
                            deliver(null, "누적 주행거리 읽기 미지원")
                        } else try { odometer.getCurrentDistanceDriven(
                            onComplete = { kilometers ->
                                // Vehicle 2.3.2 sources document this getter in km, independent of display units.
                                val value = kilometers?.toDouble()?.takeIf { it.isFinite() && it in 0.0..10_000_000.0 }
                                deliver(value?.let { (it * 1_000).roundToLong() }, if (value == null) "거리값 없음" else "누적 주행거리 수신")
                            },
                            onFailed = { deliver(null, "거리 조회 실패: " + it.javaClass.simpleName) },
                        ) } catch (error: Exception) { deliver(null, "거리 조회 실패: " + error.javaClass.simpleName) }
                    }
                },
                onFailed = { deliver(null, "거리 지원 확인 실패: " + it.javaClass.simpleName) },
            )
        } catch (error: Exception) { deliver(null, "거리 서비스 연결 실패: " + error.javaClass.simpleName) }
    }

    fun stop() {
        ++generation
        requestedAt = -10_000L
        runCatching { vehicle?.release() }
        vehicle = null
    }
}
