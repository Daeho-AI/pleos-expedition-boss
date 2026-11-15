package dev.pleos.entry.boss.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Composable internal fun JourneyScreen(game:GameState,graph:BossGraph,status:CombatStatus,navigate:(String)->Unit,playing:Boolean,gateway:GatewayState){
    val stats=graph.balance.stats(game)
    val index=(game.encounter?.bossIndex?:0).mod(graph.balance.bosses.size)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("screen-home")){
        Text("원정 기지",fontSize=32.sp,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(7.dp));Text("차량의 여정은 힘으로, 보스의 보물은 성장으로.",color=Muted,fontSize=17.sp)
        Spacer(Modifier.height(22.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(350.dp).clip(RoundedCornerShape(24.dp)).testTag("mission-card")){
            val narrow=maxWidth<850.dp
            ArenaBackdrop(index,Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xF00A1521),Color(0xA00A1521),Color.Transparent))))
            BossArt(index,Modifier.align(Alignment.CenterEnd).padding(end=if(narrow)12.dp else 40.dp).width(if(narrow)210.dp else 330.dp).height(270.dp))
            Column(Modifier.align(Alignment.CenterStart).padding(28.dp).fillMaxWidth(if(narrow).65f else .6f)){
                Text("SECTOR "+(index+1).toString().padStart(2,'0')+"  /  "+(game.encounter?.cycle?:1)+"번째 원정",color=worlds[index].light,fontSize=13.sp,letterSpacing=1.sp,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(13.dp));Text(worlds[index].name,fontSize=if(narrow)27.sp else 36.sp,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(8.dp));Text(graph.balance.bosses[index].name+"가 기다립니다",color=Muted,fontSize=17.sp)
                Spacer(Modifier.height(24.dp))
                ActionButton("보스전 입장",{if(!game.hero.autoExpeditionEnabled)graph.setEnabled(true);navigate("battle")},Modifier.widthIn(min=220.dp).testTag("enter-battle"),!playing)
                Spacer(Modifier.height(12.dp));Text("전투 전용 화면으로 이동 · 주차 중 자동 사냥",color=Muted,fontSize=12.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(9.dp)){
            graph.balance.bosses.forEachIndexed{i,boss->
                Surface(color=if(i==index)worlds[index].light.copy(alpha=.13f)else Panel,shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,if(i==index)worlds[index].light.copy(alpha=.6f)else Edge)){
                    Text((i+1).toString().padStart(2,'0')+"  "+boss.name,color=if(i==index)worlds[index].light else Muted,fontSize=13.sp,modifier=Modifier.padding(14.dp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()){
            val progress:@Composable ()->Unit={
                Surface(Modifier.fillMaxWidth(),color=Panel,shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,Edge)){
                    Column(Modifier.padding(24.dp)){
                        Row(verticalAlignment=Alignment.CenterVertically){
                            HeroPortrait(game,Modifier.size(100.dp,135.dp));Spacer(Modifier.width(24.dp))
                            Column(Modifier.weight(1f)){Text("여정의 수호자",color=Muted,fontSize=14.sp);Text("Lv. "+game.hero.level,fontSize=33.sp,fontWeight=FontWeight.Bold);Text("공격력 "+stats.attack+" · 장비 "+game.equipment.size+"개",color=Mint,fontSize=15.sp)}
                            Column(horizontalAlignment=Alignment.End){Text("보상 상자",color=Muted,fontSize=13.sp);Text(game.unopened.size.toString(),fontSize=32.sp,color=Gold,fontWeight=FontWeight.Bold)}
                        }
                        Spacer(Modifier.height(14.dp));Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                            ActionButton("보상 열기",{navigate("chests")},Modifier.weight(1f),game.unopened.isNotEmpty())
                            OutlinedButton(onClick={navigate("equipment")},modifier=Modifier.weight(1f).heightIn(min=64.dp)){Text("장비 · 강화")}
                        }
                    }
                }
            }
            if(maxWidth>=1000.dp){Row(horizontalArrangement=Arrangement.spacedBy(18.dp)){Box(Modifier.weight(1f)){VehicleJourneyCard(game,gateway)};Box(Modifier.weight(1f)){progress()}}}
            else{Column{VehicleJourneyCard(game,gateway);Spacer(Modifier.height(16.dp));progress()}}
        }
        game.hero.progress.lastReturn?.let{Spacer(Modifier.height(18.dp));ReturnCard(it,game.hero.progress.awaitingDistance)}
        Spacer(Modifier.height(22.dp));Text("운전 중에는 게임을 멈추고, 주차 후 이어갑니다",fontSize=17.sp,fontWeight=FontWeight.SemiBold)
        Text("복귀하면 최대 8시간의 보상과 확인된 이동거리의 필살기 피해를 한 번 정산합니다.",fontSize=14.sp,color=Muted)
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
