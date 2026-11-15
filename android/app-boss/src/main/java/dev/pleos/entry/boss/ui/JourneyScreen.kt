package dev.pleos.entry.boss.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pleos.boss.game.*
import dev.pleos.entry.boss.BossGraph
import java.util.Locale

@Composable internal fun JourneyScreen(game:GameState,graph:BossGraph,navigate:(String)->Unit,playing:Boolean){
    val index=(game.encounter?.bossIndex?:0).mod(graph.balance.bosses.size)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("screen-home"),verticalArrangement=Arrangement.spacedBy(24.dp)){
        BoxWithConstraints(Modifier.fillMaxWidth()){
            if(maxWidth>=1000.dp){
                Row(horizontalArrangement=Arrangement.spacedBy(24.dp)){
                    HeroHome(game,graph,Modifier.width(320.dp).height(580.dp)){navigate("equipment")}
                    MissionHome(game,graph,index,Modifier.weight(1f).height(580.dp),!playing){
                        if(!game.hero.autoExpeditionEnabled)graph.setEnabled(true)
                        navigate("battle")
                    }
                }
            }else{
                Column(verticalArrangement=Arrangement.spacedBy(20.dp)){
                    MissionHome(game,graph,index,Modifier.fillMaxWidth().height(470.dp),!playing){
                        if(!game.hero.autoExpeditionEnabled)graph.setEnabled(true)
                        navigate("battle")
                    }
                    Surface(color=Panel,shape=RoundedCornerShape(18.dp)){
                        Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically){
                            HeroPortrait(game,Modifier.size(76.dp,100.dp));Spacer(Modifier.width(20.dp))
                            Column(Modifier.weight(1f)){Text("Lv. "+game.hero.level,fontSize=26.sp,fontWeight=FontWeight.Bold);Text("공격력 "+graph.balance.stats(game).attack,color=Muted,fontSize=18.sp)}
                            ActionButton("장비",{navigate("equipment")})
                        }
                    }
                }
            }
        }
        JourneyBonus(game)
        Surface(color=Panel,shape=RoundedCornerShape(18.dp)){
            Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically){
                ChestArt(Color(0xFFE8BE73),Modifier.size(90.dp,80.dp));Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)){Text(if(game.unopened.isEmpty())"다음 보상을 기다리는 중" else "열지 않은 상자 "+game.unopened.size+"개",fontSize=23.sp,fontWeight=FontWeight.SemiBold);Spacer(Modifier.height(6.dp));Text(if(game.unopened.isEmpty())"보스를 처치하면 상자를 얻습니다." else "새 장비를 확인해 보세요.",fontSize=18.sp,color=Muted)}
                Spacer(Modifier.width(16.dp));ActionButton("보상 확인",{navigate("chests")},enabled=game.unopened.isNotEmpty())
            }
        }
    }
}

@Composable private fun HeroHome(game:GameState,graph:BossGraph,modifier:Modifier,onGear:()->Unit){
    val stats=graph.balance.stats(game)
    val xpMax=graph.balance.hero.xpToNextLevelMultiplier*game.hero.level
    Surface(modifier,color=Panel,shape=RoundedCornerShape(18.dp)){
        Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Lv. "+game.hero.level,fontSize=28.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));Text("공격력 "+stats.attack,color=Muted,fontSize=18.sp)}
            Spacer(Modifier.height(14.dp));LinearProgressIndicator(progress=if(game.hero.level>=graph.balance.hero.maxLevel)1f else (game.hero.xp.toFloat()/xpMax).coerceIn(0f,1f),modifier=Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),color=Mint,trackColor=Edge)
            Text(if(game.hero.level>=graph.balance.hero.maxLevel)"최고 레벨" else "경험치 "+game.hero.xp+" / "+xpMax,color=Muted,fontSize=16.sp,modifier=Modifier.align(Alignment.End).padding(top=8.dp))
            HeroPortrait(game,Modifier.weight(1f).fillMaxWidth().padding(vertical=14.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
                Slot.values().forEach{slot->
                    val item=game.equipment.find{it.key==game.equipped[slot]}
                    Column(horizontalAlignment=Alignment.CenterHorizontally){
                        Box(Modifier.size(68.dp).clip(RoundedCornerShape(12.dp)).background(Night),contentAlignment=Alignment.Center){
                            if(item!=null)EquipmentArt(item,slot,Modifier.size(62.dp)) else Icon(slotIcon(slot),null,tint=Muted)
                        }
                        Spacer(Modifier.height(7.dp));Text(slotName(slot),color=Muted,fontSize=16.sp)
                    }
                }
            }
            Spacer(Modifier.height(22.dp));ActionButton("장비 관리",onGear,Modifier.fillMaxWidth())
        }
    }
}

@Composable private fun MissionHome(game:GameState,graph:BossGraph,index:Int,modifier:Modifier,enabled:Boolean,onEnter:()->Unit){
    val tone=worlds[index]
    Column(modifier){
        GameTheme(dark=true){
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(18.dp)).testTag("mission-card")){
                val small=maxWidth<680.dp
                ArenaBackdrop(index,Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xED10171D),Color(0x9910171D),Color(0x3310171D)))))
                BossArt(index,Modifier.align(Alignment.CenterEnd).padding(end=20.dp,bottom=55.dp).size(if(small)180.dp else 280.dp))
                Column(Modifier.align(Alignment.TopStart).padding(28.dp)){
                    Text("원정 "+(game.encounter?.cycle?:1)+"  ·  "+(index+1)+" / "+graph.balance.bosses.size,color=Muted,fontSize=18.sp)
                    Spacer(Modifier.height(18.dp));Text(tone.name,color=Color.White,fontSize=if(small)28.sp else 36.sp,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(10.dp));Text(graph.balance.bosses[index].name,color=Color(0xFFD1D7DD),fontSize=21.sp)
                }
                Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(28.dp),verticalAlignment=Alignment.CenterVertically){
                    ActionButton(if(game.hero.autoExpeditionEnabled)"원정 계속하기" else "원정 시작",onEnter,Modifier.widthIn(min=200.dp).testTag("enter-battle"),enabled)
                    Spacer(Modifier.weight(1f))
                    if(!small)Text(if(game.hero.autoExpeditionEnabled)"자동 사냥 중" else "준비 완료",fontSize=18.sp,color=Muted)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            graph.balance.bosses.forEachIndexed{i,_->
                val active=i==index
                Box(Modifier.size(if(active)52.dp else 40.dp).clip(CircleShape).background(if(active)Ink else MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center){
                    if(i<index)Icon(Icons.Default.Check,null,tint=Muted,modifier=Modifier.size(20.dp))
                    else Text((i+1).toString(),fontSize=18.sp,fontWeight=if(active)FontWeight.Bold else FontWeight.Normal,color=if(active)Panel else Muted)
                }
                if(i<graph.balance.bosses.lastIndex)HorizontalDivider(Modifier.weight(1f).padding(horizontal=6.dp),color=Edge)
            }
        }
    }
}

@Composable private fun JourneyBonus(game:GameState){
    val summary=game.hero.progress.lastReturn
    val waiting=game.hero.progress.awaitingDistance
    var details by remember{mutableStateOf(false)}
    Surface(Modifier.fillMaxWidth().testTag("vehicle-journey-card"),color=MaterialTheme.colorScheme.surfaceVariant,shape=RoundedCornerShape(18.dp)){
        Row(Modifier.padding(24.dp).then(if(summary!=null)Modifier.testTag("return-summary") else Modifier),verticalAlignment=Alignment.CenterVertically){
            Icon(Icons.Default.DirectionsCar,null,tint=Muted,modifier=Modifier.size(30.dp));Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)){
                Text(when{waiting->"이동거리 확인 중";(summary?.strikeDamageMilli?:0)>0->"이동 보너스 적용";else->"달린 만큼 강해지는 필살기"},fontSize=22.sp,fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp));Text(when{
                    waiting->"차량 정보를 받으면 반영합니다."
                    summary!=null&&summary.strikeDamageMilli>0->String.format(Locale.KOREA,"%.1f km  ·  필살의 일격 +%,.0f",summary.creditedDistanceMeters/1000.0,summary.strikeDamageMilli/1000.0)
                    summary!=null&&summary.chests>0->"복귀 보상  ·  상자 "+summary.chests+"개"
                    else->"주차 후 돌아오면 이동거리를 반영합니다."
                },color=Muted,fontSize=18.sp)
            }
            IconButton(onClick={details=true},modifier=Modifier.size(64.dp).testTag("journey-help")){Icon(Icons.Default.Info,"이동 보너스 안내",tint=Muted)}
        }
    }
    if(details)AlertDialog(onDismissRequest={details=false},title={Text("이동 보너스")},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("운전 중에는 게임이 멈춥니다. 주차 후 돌아오면 이동거리에 따라 보스에게 필살의 일격을 가합니다.")
            Text("1 km마다 출발 시 공격력의 2배, 최대 30 km까지 반영됩니다. 피해는 보스의 남은 체력을 넘지 않습니다.")
            if(summary!=null)Text("최근 복귀: "+summary.creditedMs/60_000+"분 · 상자 "+summary.chests+"개",color=Muted)
        }
    },confirmButton={TextButton(onClick={details=false},modifier=Modifier.heightIn(min=64.dp)){Text("확인")}})
}
