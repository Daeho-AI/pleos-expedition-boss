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

@Composable internal fun JourneyScreen(game:GameState,graph:BossGraph,status:CombatStatus,wide:Boolean,navigate:(String)->Unit,playing:Boolean,shownStrikeId:String?,onStrikeShown:(String)->Unit){
    val stats=graph.balance.stats(game)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("screen-home")){
        Row(Modifier.fillMaxWidth().padding(bottom=18.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text("작은 모험, 더 강한 내일",fontSize=30.sp,fontWeight=FontWeight.Bold);Text("장비를 바꾸고, 보스를 넘고, 다음 여정을 준비하세요.",color=Muted,fontSize=16.sp)}
            ActionButton(if(game.hero.autoExpeditionEnabled)"Ⅱ  사냥 쉬기" else "▶  자동 사냥",{graph.setEnabled(!game.hero.autoExpeditionEnabled)},Modifier.testTag("enable-expedition"),!playing)
        }
        BattleScene(game,graph.balance,shownStrikeId,onStrikeShown,Modifier.fillMaxWidth().height(if(wide)620.dp else 360.dp))
        Spacer(Modifier.height(16.dp))
        Surface(Modifier.fillMaxWidth(),color=Panel,shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,Edge)){
            Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(22.dp)){
                Column(Modifier.weight(1.1f)){
                    Row(verticalAlignment=Alignment.CenterVertically){Text("Lv. "+game.hero.level,color=Gold,fontSize=29.sp,fontWeight=FontWeight.Black);Spacer(Modifier.width(13.dp));Text("여정의 수호자",fontSize=17.sp)}
                    val required=graph.balance.hero.xpToNextLevelMultiplier*game.hero.level
                    Spacer(Modifier.height(9.dp));LinearProgressIndicator(progress=if(game.hero.level==graph.balance.hero.maxLevel)1f else game.hero.xp.toFloat()/required,modifier=Modifier.widthIn(max=480.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),color=Gold,trackColor=Edge)
                    Spacer(Modifier.height(5.dp));Text(if(game.hero.level==graph.balance.hero.maxLevel)"최고 레벨" else "성장  "+game.hero.xp+" / "+required+" XP",color=Muted,fontSize=12.sp)
                }
                Column(Modifier.width(90.dp)){Stat("공격력",stats.attack.toString())}
                Column(Modifier.width(90.dp)){Stat("수집 장비",game.equipment.size.toString())}
                VerticalDivider(Modifier.height(65.dp),color=Edge)
                ChestArt(Gold,Modifier.size(76.dp))
                Column{Text("보물 상자",color=Muted,fontSize=14.sp);Text(game.unopened.size.toString()+"개",fontSize=27.sp,fontWeight=FontWeight.Bold)}
                ActionButton("보상 열기",{navigate("chests")},enabled=game.unopened.isNotEmpty())
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(14.dp)){
            Slot.values().forEach{slot->
                val item=game.equipment.find{it.key==game.equipped[slot]}
                Surface(Modifier.weight(1f).clickable{navigate("equipment")},color=Panel,shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Edge)){
                    Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                        if(item!=null)EquipmentArt(item,slot,Modifier.size(68.dp)) else Icon(slotIcon(slot),null,tint=Muted,modifier=Modifier.padding(16.dp).size(36.dp))
                        Spacer(Modifier.width(10.dp));Column{Text(slotName(slot),color=Muted,fontSize=12.sp);Text(item?.let{graph.balance.template(it.templateId).name+" +"+it.upgradeLevel}?:"장착해 보세요",color=if(item!=null)rarityInk(item.rarity) else Muted,fontSize=17.sp,fontWeight=FontWeight.SemiBold)}
                    }
                }
            }
        }
        game.hero.progress.lastReturn?.let{Spacer(Modifier.height(14.dp));ReturnCard(it,game.hero.progress.awaitingDistance)}
        Spacer(Modifier.height(18.dp))
        Text("이동 후, 필살의 일격",color=Gold,fontSize=20.sp,fontWeight=FontWeight.Bold)
        Text("주행 중에는 게임을 멈추고, 주차 후 비운 시간을 정산합니다. 확인된 이동 1km마다 출발 공격력의 2배를 한 번에!",color=Muted,fontSize=15.sp)
        Text("비운 시간 최대 8시간 · 이동 최대 30km · 현재 보스의 남은 체력까지",color=Muted,fontSize=13.sp)
        if(BuildConfig.IS_FIXTURE){
            Spacer(Modifier.height(18.dp));Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                ActionButton("7분 비움 · 10km 복귀",{graph.simulateTrip()},Modifier.weight(1f).testTag("fixture-run"),!playing)
                OutlinedButton(onClick={graph.simulateTrip(10,10.0)},enabled=!playing,modifier=Modifier.weight(1f).heightIn(min=64.dp)){Text("10km 필살기 시연")}
            }
            Text("시연 데이터 · 실제 주행 아님",color=Gold,fontSize=12.sp,modifier=Modifier.padding(top=8.dp))
        }
        Spacer(Modifier.height(14.dp));Text(status.reason,color=Muted,fontSize=12.sp)
    }
}
