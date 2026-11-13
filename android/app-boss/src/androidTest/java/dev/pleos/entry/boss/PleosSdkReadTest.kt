package dev.pleos.entry.boss

import android.car.Car
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pleos.boss.game.UxMode
import dev.pleos.entry.boss.sdk.PleosVehicleGateway
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** A read-only integration check against the official emulator, never fixture values. */
@RunWith(AndroidJUnit4::class)
class PleosSdkReadTest {
    @Test fun parkedOdometerReadAndBackgroundRelease() = runBlocking {
        assumeTrue(Build.FINGERPRINT.startsWith("Pleos/crp_avd/"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        for (permission in listOf(Car.PERMISSION_SPEED, Car.PERMISSION_POWERTRAIN)) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
                automation.grantRuntimePermission(context.packageName, permission)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val gateway = PleosVehicleGateway(context, scope)
        try {
            withContext(Dispatchers.Main) { gateway.activityVisible = true; gateway.start() }
            val received = withTimeoutOrNull(30_000) {
                while (gateway.state.value.ux != UxMode.PARKED_INTERACTIVE ||
                    gateway.state.value.distance() == null) delay(100)
                true
            } ?: false
            val source = gateway.state.value
            val report = JSONObject().apply {
                put("source", source.source)
                put("vehicleSdk", "2.3.2")
                put("api", "Odometer.getCurrentDistanceDriven")
                put("apiUnit", "km")
                put("storedUnit", "meters")
                put("recordedAtEpochMs", System.currentTimeMillis())
                put("deviceFingerprint", Build.FINGERPRINT)
                put("ux", source.ux.name)
                put("odometerStatus", source.odometerStatus)
                put("odometerMeters", source.odometerMeters ?: JSONObject.NULL)
                put("note", "Read-only parked emulator observation. No vehicle value was injected; no actual journey was performed.")
            }
            File(context.filesDir, "sdk-odometer-0.3.json").writeText(report.toString(2))
            assertEquals("PLEOS_SDK", source.source)
            assertTrue("No fresh Vehicle SDK odometer reading: ${source.ux}, ${source.odometerStatus}", received)
            assertNotNull(source.odometerMeters)
            withContext(Dispatchers.Main) { gateway.activityVisible = false; gateway.stop() }
            val stopped = gateway.state.value
            delay(1_200)
            assertNull(stopped.odometerMeters)
            assertEquals(stopped, gateway.state.value)
        } finally {
            withContext(Dispatchers.Main) { gateway.stop() }
            scope.cancel()
        }
    }
}
