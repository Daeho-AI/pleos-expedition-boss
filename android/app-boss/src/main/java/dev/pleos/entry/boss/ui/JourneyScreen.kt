package dev.pleos.entry.boss.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pleos.boss.game.*
import dev.pleos.entry.boss.BossGraph
import dev.pleos.entry.boss.BuildConfig
import dev.pleos.entry.boss.sdk.GatewayState
import java.util.Locale

@Composable internal fun JourneyScreen(game:GameState,graph:BossGraph,status:CombatStatus,navigate:(String)->Unit,playing:Boolean,shownStrikeId:String?,onStrikeShown:(String)->Unit,gateway:GatewayState){
    var compact by rememberSaveable{mutableStateOf(true)}
    val stats=graph.balance.stats(game)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("screen-home")){
        Text("주차 후, 여정의 보상을 확인하세요",fontSize=29.sp,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(8.dp));Text("차량의 이동이 원정의 힘으로 이어집니다. 작은 화면에서 자동 사냥을 즐겨 보세요.",color=Muted,fontSize=16.sp)
        Spacer(Modifier.height(24.dp))
        val overview:@Composable ColumnScope.()->Unit={
            VehicleJourneyCard(game,gateway)
            Spacer(Modifier.height(16.dp))
            game.hero.progress.lastReturn?.let{ReturnCard(it,game.hero.progress.awaitingDistance);Spacer(Modifier.height(16.dp))}
            Surface(Modifier.fillMaxWidth(),color=Panel,shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,Edge)){
                Column(Modifier.padding(22.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){Text("내 원정의 성장",color=Muted,fontSize=14.sp);Spacer(Modifier.height(8.dp));Text("Lv. "+game.hero.level,fontSize=32.sp,fontWeight=FontWeight.Bold);Text("공격력 "+stats.attack+"  ·  수집 장비 "+game.equipment.size+"개",color=Muted,fontSize=14.sp)}
                        Column(horizontalAlignment=Alignment.End){Text("열지 않은 보상",color=Muted,fontSize=14.sp);Text(game.unopened.size.toString()+"개",fontSize=29.sp,color=Gold,fontWeight=FontWeight.Bold);Spacer(Modifier.height(6.dp));ActionButton("보상 열기",{navigate("chests")},enabled=game.unopened.isNotEmpty())}
                    }
                    Spacer(Modifier.height(18.dp));LinearProgressIndicator(progress=if(game.hero.level==graph.balance.hero.maxLevel)1f else game.hero.xp.toFloat()/(graph.balance.hero.xpToNextLevelMultiplier*game.hero.level),modifier=Modifier.fillMaxWidth().height(5.dp),color=Mint,trackColor=Edge)
                }
            }
            Spacer(Modifier.height(16.dp));Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                OutlinedButton(onClick={navigate("equipment")},modifier=Modifier.weight(1f).heightIn(min=64.dp)){Icon(Icons.Default.Shield,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("장비 · 강화")}
                OutlinedButton(onClick={navigate("friends")},modifier=Modifier.weight(1f).heightIn(min=64.dp)){Icon(Icons.Default.People,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("친구 · 교환")}
            }
        }
        val mini:@Composable ()->Unit={
            Column(Modifier.testTag("mini-expedition-panel")){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("작은 원정",color=Muted,fontSize=16.sp,modifier=Modifier.weight(1f));TextButton(onClick={compact=!compact},modifier=Modifier.heightIn(min=48.dp).testTag("toggle-panel-size")){Text(if(compact)"크게 보기" else "작게 보기",fontSize=13.sp)}}
                BattleScene(game,graph.balance,shownStrikeId,onStrikeShown,Modifier.fillMaxWidth().height(if(compact)480.dp else 600.dp))
                Spacer(Modifier.height(12.dp));OutlinedButton(onClick={graph.setEnabled(!game.hero.autoExpeditionEnabled)},enabled=!playing,modifier=Modifier.fillMaxWidth().heightIn(min=64.dp).testTag("enable-expedition")){Text(if(game.hero.autoExpeditionEnabled)"Ⅱ  사냥 쉬기" else "▶  자동 사냥",fontSize=16.sp)}
                Spacer(Modifier.height(8.dp));Text("주차 중에만 표시됩니다",fontSize=12.sp,color=Muted,modifier=Modifier.align(Alignment.CenterHorizontally))
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()){
            val availableWidth=maxWidth
            if(maxWidth>=720.dp){
                val panelWidth=if(maxWidth<1000.dp){if(compact)280.dp else 340.dp}else{if(compact)320.dp else 410.dp}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp)){
                    Column(Modifier.weight(1f),content=overview)
                    Box(Modifier.width(panelWidth)){mini()}
                }
            }else{
                Column(horizontalAlignment=Alignment.CenterHorizontally){
                    Box(Modifier.widthIn(max=availableWidth).width(if(compact)280.dp else 360.dp)){mini()}
                    Spacer(Modifier.height(20.dp))
                    Column(content=overview)
                }
            }
        }
        Spacer(Modifier.height(24.dp));Text("운전이 시작되면 게임을 멈춥니다",fontSize=17.sp,fontWeight=FontWeight.SemiBold)
        Text("도착 후 게임으로 돌아오면 최대 8시간의 보상과, 확인된 이동거리의 필살기를 한 번 정산합니다.",fontSize=14.sp,color=Muted)
        if(BuildConfig.IS_FIXTURE){
            Spacer(Modifier.height(16.dp));OutlinedButton(onClick={graph.simulateTrip()},enabled=!playing,modifier=Modifier.heightIn(min=64.dp).testTag("fixture-run")){Text("시연  ·  7분 비움 / 10km 복귀")}
            Text("가상 입력입니다. 실제 주행이나 SDK 이동값이 아닙니다.",fontSize=12.sp,color=Gold,modifier=Modifier.padding(top=6.dp))
        }
        Spacer(Modifier.height(12.dp));Text(status.reason,color=Muted,fontSize=11.sp)
    }
}
@Composable private fun VehicleJourneyCard(game:GameState,gateway:GatewayState){
    val summary=game.hero.progress.lastReturn
    Surface(Modifier.fillMaxWidth().testTag("vehicle-journey-card"),color=Color(0xFF1B2C3F),shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,Color(0xFF354C62))){
        Column(Modifier.padding(24.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Default.DirectionsCar,null,tint=Mint,modifier=Modifier.size(31.dp));Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)){Text("PLEOS CONNECT",color=Mint,fontSize=13.sp,letterSpacing=2.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(3.dp));Text("차량과 이어지는 원정",fontSize=22.sp,fontWeight=FontWeight.Bold)}
                BadgePill("P · 주차 확인",Mint)
            }
            Spacer(Modifier.height(27.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(32.dp)){
                Column(Modifier.weight(1f)){Text("이번 복귀 이동거리",fontSize=13.sp,color=Muted);Spacer(Modifier.height(8.dp));Text(if(summary?.distanceChecked==true)String.format(Locale.KOREA,"%.1f km",summary.creditedDistanceMeters/1000.0) else "확인 전",fontSize=30.sp,fontWeight=FontWeight.Bold)}
                Column(Modifier.weight(1f)){Text("필살의 일격",fontSize=13.sp,color=Muted);Spacer(Modifier.height(8.dp));Text(if((summary?.strikeDamageMilli?:0)>0)String.format(Locale.KOREA,"+%.0f 피해",summary!!.strikeDamageMilli/1000.0) else "이동 후 적용",fontSize=27.sp,color=Gold,fontWeight=FontWeight.Bold)}
            }
            Spacer(Modifier.height(22.dp));HorizontalDivider(color=Edge);Spacer(Modifier.height(14.dp))
            Text((if(BuildConfig.IS_FIXTURE)"시연 누적거리  " else "차량 누적거리  ")+(gateway.odometerMeters?.let{String.format(Locale.KOREA,"%.1f km",it/1000.0)}?:"확인 대기"),color=Muted,fontSize=14.sp)
            Text(if(BuildConfig.IS_FIXTURE)"가상 데이터로 기능을 체험하고 있습니다" else "차량의 누적거리 차이를 복귀 시 반영합니다",color=if(BuildConfig.IS_FIXTURE)Gold else Mint,fontSize=12.sp,modifier=Modifier.padding(top=5.dp))
        }
    }
}
