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
    val xpMax=graph.balance.hero.xpToNextLevelMultiplier*game.hero.level
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("screen-home"),verticalArrangement=Arrangement.spacedBy(20.dp)){
        Row(Modifier.fillMaxWidth().height(42.dp),verticalAlignment=Alignment.CenterVertically){
            Text("Lv. "+game.hero.level,fontSize=25.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.width(24.dp));Text("공격력 "+graph.balance.stats(game).attack,color=Muted,fontSize=19.sp)
            Spacer(Modifier.weight(1f))
            Column(Modifier.widthIn(max=240.dp).weight(.4f,false)){
                LinearProgressIndicator(progress=if(game.hero.level>=graph.balance.hero.maxLevel)1f else (game.hero.xp.toFloat()/xpMax).coerceIn(0f,1f),modifier=Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),color=Mint,trackColor=Edge)
                Text(if(game.hero.level>=graph.balance.hero.maxLevel)"MAX" else game.hero.xp.toString()+" / "+xpMax,color=Muted,fontSize=14.sp,modifier=Modifier.align(Alignment.End).padding(top=4.dp))
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()){
            val small=maxWidth<760.dp
            Surface(Modifier.fillMaxWidth().testTag("vehicle-journey-card"),color=Panel,shape=RoundedCornerShape(22.dp)){
                Column{
                    Row(Modifier.fillMaxWidth().padding(horizontal=28.dp,vertical=22.dp),verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){
                            Text("원정 "+(game.encounter?.cycle?:1)+" · "+(index+1)+"/"+graph.balance.bosses.size,color=Muted,fontSize=17.sp)
                            Spacer(Modifier.height(8.dp));Text(worlds[index].name,fontSize=if(small)27.sp else 32.sp,fontWeight=FontWeight.Bold)
                        }
                        if(game.hero.autoExpeditionEnabled)BadgePill("사냥 중",Mint)
                    }
                    LobbyScene(game,index,Modifier.fillMaxWidth().height(if(small)300.dp else 370.dp))
                    Row(Modifier.fillMaxWidth().padding(24.dp),verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){
                            Text(graph.balance.bosses[index].name,fontSize=if(small)18.sp else 22.sp,fontWeight=FontWeight.SemiBold)
                            Row(Modifier.padding(top=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                                graph.balance.bosses.forEachIndexed{i,_->Box(Modifier.size(if(small)20.dp else 30.dp,4.dp).clip(CircleShape).background(if(i<=index)Mint else Edge))}
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Button(onClick={if(!game.hero.autoExpeditionEnabled)graph.setEnabled(true);navigate("battle")},enabled=!playing,modifier=Modifier.height(72.dp).widthIn(min=if(small)170.dp else 240.dp).testTag("enter-battle"),shape=RoundedCornerShape(15.dp),colors=ButtonDefaults.buttonColors(containerColor=Mint,contentColor=MaterialTheme.colorScheme.onPrimary)){
                            Text("보스전 입장",fontSize=21.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.width(16.dp));Icon(Icons.Default.ArrowForward,null)
                        }
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()){
            if(maxWidth>=850.dp)Row(horizontalArrangement=Arrangement.spacedBy(20.dp)){
                LoadoutShortcut(game,Modifier.weight(1f)){navigate("equipment")}
                RewardShortcut(game,Modifier.weight(1f)){navigate("chests")}
            }else Column(verticalArrangement=Arrangement.spacedBy(16.dp)){
                LoadoutShortcut(game,Modifier.fillMaxWidth()){navigate("equipment")}
                RewardShortcut(game,Modifier.fillMaxWidth()){navigate("chests")}
            }
        }
        ReturnReceipt(game)
    }
}
@Composable private fun LoadoutShortcut(game:GameState,modifier:Modifier,onClick:()->Unit){
    Surface(modifier.testTag("lobby-equipment").clip(RoundedCornerShape(18.dp)).clickable(onClick=onClick),color=Panel){
        Row(Modifier.heightIn(min=116.dp).padding(horizontal=22.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically){
            Column{Text("장비",fontSize=23.sp,fontWeight=FontWeight.SemiBold);Spacer(Modifier.height(6.dp));Text("장착 · 강화",color=Muted,fontSize=17.sp)}
            Spacer(Modifier.weight(1f))
            Slot.values().forEach{slot->
                val item=game.equipment.find{it.key==game.equipped[slot]}
                Box(Modifier.padding(start=7.dp).size(62.dp).clip(RoundedCornerShape(12.dp)).background(Night),contentAlignment=Alignment.Center){
                    if(item!=null)EquipmentArt(item,slot,Modifier.size(58.dp)) else Icon(slotIcon(slot),null,tint=Muted)
                }
            }
            Spacer(Modifier.width(12.dp));Icon(Icons.Default.ChevronRight,null,tint=Muted)
        }
    }
}
@Composable private fun RewardShortcut(game:GameState,modifier:Modifier,onClick:()->Unit){
    Surface(modifier.testTag("lobby-rewards").clip(RoundedCornerShape(18.dp)).clickable(onClick=onClick),color=Panel){
        Row(Modifier.heightIn(min=116.dp).padding(horizontal=22.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically){
            ChestArt(Color(0xFFE8BE73),Modifier.size(80.dp,70.dp));Spacer(Modifier.width(22.dp))
            Column(Modifier.weight(1f)){Text("보상 상자",fontSize=23.sp,fontWeight=FontWeight.SemiBold);Spacer(Modifier.height(6.dp));Text(if(game.unopened.isEmpty())"보관함이 비어 있어요" else game.unopened.size.toString()+"개 보관 중",color=Muted,fontSize=17.sp)}
            Icon(Icons.Default.ChevronRight,null,tint=Muted)
        }
    }
}
@Composable private fun ReturnReceipt(game:GameState){
    val summary=game.hero.progress.lastReturn?:return
    var details by remember{mutableStateOf(false)}
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable{details=true}.testTag("return-summary").padding(horizontal=12.dp,vertical=18.dp),verticalAlignment=Alignment.CenterVertically){
        Icon(Icons.Default.History,null,tint=Muted,modifier=Modifier.size(22.dp));Spacer(Modifier.width(12.dp));Text("지난 원정",fontSize=17.sp,color=Muted)
        Spacer(Modifier.weight(1f))
        Text(if(summary.strikeDamageMilli>0)String.format(Locale.KOREA,"%.1f km · 일격 +%,.0f",summary.creditedDistanceMeters/1000.0,summary.strikeDamageMilli/1000.0) else "보상 상자 "+summary.chests+"개",fontSize=17.sp,color=Muted)
        Spacer(Modifier.width(10.dp));Icon(Icons.Default.ChevronRight,null,tint=Muted)
    }
    if(details)AlertDialog(onDismissRequest={details=false},title={Text("지난 원정")},text={
        Column(verticalArrangement=Arrangement.spacedBy(16.dp)){
            Text("경과 시간  "+summary.creditedMs/60_000+"분")
            Text("보상 상자  "+summary.chests+"개")
            Text(String.format(Locale.KOREA,"이동 거리  %.1f km",summary.creditedDistanceMeters/1000.0))
            Text(String.format(Locale.KOREA,"필살의 일격  +%,.0f",summary.strikeDamageMilli/1000.0))
        }
    },confirmButton={TextButton(onClick={details=false},modifier=Modifier.heightIn(min=64.dp)){Text("확인")}})
}
