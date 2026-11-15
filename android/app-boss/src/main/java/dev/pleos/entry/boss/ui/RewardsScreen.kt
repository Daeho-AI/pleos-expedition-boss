package dev.pleos.entry.boss.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.pleos.boss.game.*
import dev.pleos.entry.boss.BossGraph

@Composable internal fun ChestScreen(game:GameState,graph:BossGraph,busy:Boolean){
    var selected by remember{mutableStateOf<String?>(null)}
    var openedTab by remember{mutableStateOf(false)}
    val revealed=game.chests.find{it.id==selected&&it.openedAt!=null}
    Column(Modifier.fillMaxSize().testTag("screen-chests")){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text("보상",fontSize=30.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if(!openedTab&&game.unopened.isNotEmpty())ActionButton("모두 열기 · "+game.unopened.size,{graph.openAll()},Modifier.testTag("open-all"),!busy)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FilterChip(selected=!openedTab,onClick={openedTab=false},label={Text("받은 상자")},modifier=Modifier.heightIn(min=64.dp))
            FilterChip(selected=openedTab,onClick={openedTab=true},label={Text("획득 기록")},modifier=Modifier.heightIn(min=64.dp).testTag("reward-history"))
        }
        Spacer(Modifier.height(16.dp))
        val visible=game.chests.asReversed().filter{(it.openedAt!=null)==openedTab}
        if(visible.isEmpty())Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){
            Column(horizontalAlignment=Alignment.CenterHorizontally){
                ChestArt(Color(0xFFE2B76F),Modifier.size(150.dp))
                Spacer(Modifier.height(24.dp));Text(if(openedTab)"아직 받은 장비가 없습니다" else if(game.chests.isEmpty())"아직 받은 상자가 없습니다" else "모든 상자를 열었습니다",fontSize=26.sp,fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp));Text(if(openedTab)"상자를 열면 획득한 장비를 볼 수 있습니다." else "다음 보스를 처치하고 새 상자를 얻으세요.",color=Muted)
            }
        }
        else LazyVerticalGrid(columns=GridCells.Adaptive(260.dp),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp),modifier=Modifier.weight(1f)){
            items(visible,key={it.id}){chest->
                CardBox{
                    if(chest.openedAt==null){
                        ChestArt(Color(0xFFE2B76F),Modifier.fillMaxWidth().height(150.dp))
                        Spacer(Modifier.height(12.dp));Text(graph.balance.bosses.find{it.id==chest.bossId}!!.name,fontSize=22.sp,fontWeight=FontWeight.SemiBold)
                        Text("보스 상자",color=Muted,fontSize=18.sp)
                        Spacer(Modifier.height(20.dp));ActionButton("열기",{selected=chest.id;graph.openChest(chest.id)},Modifier.fillMaxWidth().testTag("open-chest-"+chest.id),!busy)
                    }else{
                        val t=graph.balance.template(chest.templateId)
                        Text(rarityName(chest.rarity),fontSize=16.sp,color=rarityColor(chest.rarity))
                        EquipmentArt(Equipment(chest.templateId,chest.rarity),t.slot,Modifier.fillMaxWidth().height(150.dp))
                        Text(t.name,fontSize=22.sp,fontWeight=FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp));Text("코인 +"+chest.coins+" · 조각 +1",color=Muted,fontSize=18.sp)
                        if(chest.convertedDust>0)Text("중복 · 가루 +"+chest.convertedDust,color=Muted,fontSize=16.sp)
                    }
                }
            }
        }
    }
    if(revealed!=null)Dialog(onDismissRequest={selected=null}){
        CardBox(Modifier.widthIn(max=520.dp).fillMaxWidth().verticalScroll(rememberScrollState())){
            val t=graph.balance.template(revealed.templateId)
            val key=revealed.templateId+":"+revealed.rarity.name
            val equipped=game.equipped[t.slot]==key
            Text("획득한 보상",fontSize=24.sp,fontWeight=FontWeight.Bold)
            LootReveal(Equipment(revealed.templateId,revealed.rarity),t.slot,Modifier.align(Alignment.CenterHorizontally).size(220.dp))
            Text(rarityName(revealed.rarity)+" · "+slotName(t.slot),color=rarityColor(revealed.rarity),fontSize=18.sp)
            Spacer(Modifier.height(8.dp));Text(t.name,fontSize=30.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(12.dp));Text("코인 +"+revealed.coins+" · 도감 조각 +1",color=Muted)
            if(revealed.convertedDust>0)Text("중복 장비 · 가루 +"+revealed.convertedDust,color=Muted)
            Spacer(Modifier.height(16.dp));Text(comparison(game,graph.balance,key,t.slot),color=Mint,fontSize=20.sp)
            Spacer(Modifier.height(24.dp));ActionButton(if(equipped)"장착 중" else "장착",{graph.equip(key);selected=null},Modifier.fillMaxWidth(),!busy&&!equipped)
            TextButton(onClick={selected=null},modifier=Modifier.fillMaxWidth().heightIn(min=64.dp)){Text("확인")}
        }
    }
}
