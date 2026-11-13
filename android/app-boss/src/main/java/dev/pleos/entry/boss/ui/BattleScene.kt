package dev.pleos.entry.boss.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pleos.boss.game.GameState
import dev.pleos.boss.game.ReturnSummary
import kotlinx.coroutines.delay
import java.util.Locale

/** Composed only in the parked, foreground game tree; it never owns reward delivery. */
@Composable internal fun BattleScene(game: GameState, modifier: Modifier = Modifier) {
    val hit = remember { Animatable(0f) }
    var previousHp by remember { mutableStateOf<Long?>(null) }
    val summary = game.hero.progress.lastReturn
    var shownStrike by rememberSaveable { mutableStateOf<String?>(null) }
    var activeStrike by remember { mutableStateOf<ReturnSummary?>(null) }
    // Render the captured hit, never dereference a summary that can change during the animation.
    val strike = activeStrike?.takeIf { it.id == summary?.id && (summary?.strikeDamageMilli ?: 0) > 0 }
    val striking = strike != null
    LaunchedEffect(game.encounter?.id, game.encounter?.hpMilli) {
        val hp = game.encounter?.hpMilli
        if (previousHp != null && hp != previousHp && game.hero.autoExpeditionEnabled) {
            hit.snapTo(1f)
            hit.animateTo(0f, tween(500))
        }
        previousHp = hp
    }
    LaunchedEffect(summary?.id, summary?.strikeDamageMilli) {
        activeStrike = null
        if (summary != null && summary.strikeDamageMilli > 0 && shownStrike != summary.id) {
            shownStrike = summary.id
            activeStrike = summary
            try { delay(1_800) }
            finally { if (activeStrike?.id == summary.id) activeStrike = null }
        }
    }
    Box(modifier.testTag("battle-scene")) {
        BossArt(strike?.strikeBossIndex ?: game.encounter?.bossIndex ?: 0,
            Modifier.fillMaxSize().graphicsLayer { translationX = hit.value * 8f })
        if (hit.value > 0f || striking) Canvas(Modifier.fillMaxSize()) {
            val alpha = if (striking) .9f else hit.value
            drawLine(Gold.copy(alpha = alpha), Offset(size.width * .35f, size.height * .7f),
                Offset(size.width * .67f, size.height * .2f), if (striking) 14f else 5f, StrokeCap.Round)
            if (striking) drawLine(Color.White.copy(alpha = .9f), Offset(size.width * .4f, size.height * .7f),
                Offset(size.width * .72f, size.height * .2f), 4f, StrokeCap.Round)
        }
        if (strike != null) Column(Modifier.align(Alignment.BottomCenter).background(Night.copy(alpha = .9f)).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("필살의 일격", color = Gold, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(String.format(Locale.KOREA, "%.1f 피해 · %.1f km의 힘", strike.strikeDamageMilli / 1_000.0,
                strike.creditedDistanceMeters / 1_000.0), color = Color.White, fontSize = 17.sp)
        }
    }
}

@Composable internal fun ReturnCard(summary: ReturnSummary, awaitingDistance: Boolean) {
    CardBox(Modifier.fillMaxWidth().testTag("return-summary")) {
        Text("복귀 정산 완료", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("비운 시간 " + summary.creditedMs / 60_000 + "분 반영 · 보스 " + summary.bosses + "마리 · 상자 " + summary.chests + "개")
        Spacer(Modifier.height(6.dp))
        Text(when {
            awaitingDistance -> "이동거리를 확인하고 있어요. 기본 보상과 자동 사냥은 계속됩니다."
            summary.strikeDamageMilli > 0 -> String.format(Locale.KOREA, "필살의 일격 · %.1f km · %.1f 피해",
                summary.creditedDistanceMeters / 1_000.0, summary.strikeDamageMilli / 1_000.0)
            else -> "이번에는 확인된 추가 이동거리가 없어요."
        }, color = Mint, fontSize = 15.sp)
        if (summary.elapsedMs > summary.creditedMs) Text("비운 시간은 최대 8시간까지 반영합니다.", color = Muted, fontSize = 13.sp)
        if (summary.distanceMeters > summary.creditedDistanceMeters) Text("필살기는 한 번에 최대 30km를 반영합니다.", color = Muted, fontSize = 13.sp)
    }
}
