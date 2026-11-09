package dev.pleos.entry.boss

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class ExpeditionService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recording: Job? = null
    private val graph get() = (application as BossApplication).graph
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("journey", "원정 기록", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, ExpeditionService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, "journey")
            .setSmallIcon(R.drawable.ic_launcher).setContentTitle("원정 보스 · 기록 준비")
            .setContentText("차량과 내비 데이터가 유효한 동안 여정을 기록합니다.")
            .setContentIntent(open).setOngoing(true)
            .addAction(0, "기록 중지", stop).build()
        startForeground(11, notification)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            graph.setEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (recording?.isActive != true) {
            recording = serviceScope.launch {
                graph.repository.ready.await()
                graph.gateway.start()
                while (isActive && graph.repository.state.value.hero.autoExpeditionEnabled && graph.fatalError.value == null) {
                    withContext(Dispatchers.IO) { graph.tick() }
                    delay(1000)
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        recording?.cancel()
        serviceScope.cancel()
        if (!graph.activityVisible) graph.gateway.stop()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
