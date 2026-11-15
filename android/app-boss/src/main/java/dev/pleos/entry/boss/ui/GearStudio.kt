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

@Composable internal fun GearStudio(game:GameState,graph:BossGraph,busy:Boolean){
    var filter by remember{mutableStateOf<Slot?>(null)}
    var selectedKey by remember{mutableStateOf<String?>(null)}
    val selected=game.equipment.find{it.key==selectedKey}
    Column(Modifier.fillMaxSize().testTag("screen-equipment")){
        Title("장비","")
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()){
            val wide=maxWidth>=820.dp
            Row(horizontalArrangement=Arrangement.spacedBy(24.dp)){
                if(wide)GearDetails(game,graph,selected,busy,Modifier.width(340.dp).fillMaxHeight())
                Column(Modifier.weight(1f)){
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        FilterChip(selected=filter==null,onClick={filter=null},label={Text("전체 "+game.equipment.size)},modifier=Modifier.heightIn(min=64.dp))
                        Slot.values().forEach{slot->FilterChip(selected=filter==slot,onClick={filter=slot},label={Text(slotName(slot))},modifier=Modifier.heightIn(min=64.dp).testTag("filter-"+slot.name))}
                    }
                    Spacer(Modifier.height(12.dp))
                    if(game.equipment.isEmpty())Text("상자를 열어 첫 장비를 얻으세요.",color=Muted,modifier=Modifier.padding(24.dp))
                    LazyVerticalGrid(columns=GridCells.Adaptive(190.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.weight(1f)){
                        items(game.equipment.filter{filter==null||graph.balance.template(it.templateId).slot==filter}.sortedWith(compareByDescending<Equipment>{it.key in game.equipped.values}.thenByDescending{it.rarity.ordinal}.thenByDescending{it.upgradeLevel}.thenBy{it.templateId}),key={it.key}){item->
                            val t=graph.balance.template(item.templateId);val worn=game.equipped[t.slot]==item.key;val tint=rarityColor(item.rarity)
                            Surface(Modifier.clickable{selectedKey=item.key}.testTag("gear-"+item.key),color=Panel,shape=RoundedCornerShape(16.dp),border=if(selectedKey==item.key)BorderStroke(2.dp,Ink) else null){
                                Column(Modifier.padding(16.dp)){
                                    Row(Modifier.fillMaxWidth()){Text(rarityName(item.rarity),color=tint,fontSize=16.sp);Spacer(Modifier.weight(1f));if(worn)Text("장착 중",color=Mint,fontSize=16.sp)}
                                    EquipmentArt(item,t.slot,Modifier.fillMaxWidth().height(135.dp))
                                    Text(t.name,fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                                    Text("+"+item.upgradeLevel,color=Muted,fontSize=18.sp,modifier=Modifier.padding(top=6.dp))
                                }
                            }
                        }
                    }
                }
            }
            if(!wide&&selected!=null)Dialog(onDismissRequest={selectedKey=null}){
                Surface(shape=RoundedCornerShape(18.dp),color=Panel){
                    Column(Modifier.fillMaxWidth().heightIn(max=800.dp)){
                        GearDetails(game,graph,selected,busy,Modifier.weight(1f,false).fillMaxWidth(),compact=true)
                        TextButton(onClick={selectedKey=null},modifier=Modifier.fillMaxWidth().heightIn(min=64.dp)){Text("닫기")}
                    }
                }
            }
        }
    }
}

@Composable private fun GearDetails(game:GameState,graph:BossGraph,selected:Equipment?,busy:Boolean,modifier:Modifier,compact:Boolean=false){
    val template=selected?.let{graph.balance.template(it.templateId)}
    val equipped=selected!=null&&template!=null&&game.equipped[template.slot]==selected.key
    val preview=if(selected!=null&&template!=null)game.copy(equipped=game.equipped+(template.slot to selected.key)) else game
    val stats=graph.balance.stats(preview)
    Surface(modifier,shape=RoundedCornerShape(18.dp),color=Panel){
        Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally){
            Row(Modifier.fillMaxWidth()){Text("Lv. "+game.hero.level,fontSize=24.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));Text(if(selected!=null&&!equipped)"미리보기" else "현재 장비",color=Muted,fontSize=18.sp)}
            if(compact){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){
                    HeroPortrait(preview,Modifier.size(150.dp).testTag("hero-preview"))
                    Spacer(Modifier.width(28.dp));Stat("공격력",stats.attack.toString())
                }
            }else{
                HeroPortrait(preview,Modifier.fillMaxWidth().height(230.dp).padding(vertical=10.dp).testTag("hero-preview"))
                Text(heroLook(preview).motion.title,color=Muted,fontSize=18.sp)
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){Stat("공격력",stats.attack.toString())}
            }
            Spacer(Modifier.height(if(compact)12.dp else 24.dp));HorizontalDivider(color=Edge);Spacer(Modifier.height(if(compact)16.dp else 24.dp))
            if(selected!=null&&template!=null){
                val next=selected.upgradeLevel+1
                val maxUpgrade=graph.balance.upgrades.maxLevel
                val cost=next*graph.balance.upgrades.coinMultiplier
                val dustCost=next*graph.balance.upgrades.dustMultiplier
                val canUpgrade=selected.upgradeLevel<maxUpgrade&&game.hero.coins>=cost&&game.hero.dust>=dustCost
                Text(rarityName(selected.rarity)+" · "+slotName(template.slot),color=rarityColor(selected.rarity),fontSize=18.sp)
                Spacer(Modifier.height(8.dp));Text(template.name+" +"+selected.upgradeLevel,fontSize=26.sp,fontWeight=FontWeight.Bold)
                if(!equipped){Spacer(Modifier.height(10.dp));Text(comparison(game,graph.balance,selected.key,template.slot),color=Mint,fontSize=18.sp)}
                Spacer(Modifier.height(24.dp));ActionButton(if(equipped)"장착 중" else "장착",{graph.equip(selected.key)},Modifier.fillMaxWidth().testTag("equip-selected"),!busy&&!equipped)
                if(selected.upgradeLevel<maxUpgrade){Spacer(Modifier.height(16.dp));Text("코인 "+cost+" · 가루 "+dustCost+(if(!canUpgrade)" 부족" else ""),fontSize=16.sp,color=Muted)}
                Spacer(Modifier.height(8.dp));OutlinedButton(onClick={graph.upgrade(selected.key)},enabled=!busy&&canUpgrade,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().heightIn(min=64.dp).testTag("upgrade-selected")){
                    Text(if(selected.upgradeLevel>=maxUpgrade)"최대 강화" else "+"+next+" 강화",fontSize=19.sp)
                }
            }else{
                Slot.values().forEach{slot->val item=game.equipment.find{it.key==game.equipped[slot]}
                    Row(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Text(slotName(slot),color=Muted,modifier=Modifier.width(70.dp),fontSize=18.sp);Text(item?.let{graph.balance.template(it.templateId).name+" +"+it.upgradeLevel}?:"없음",fontSize=18.sp)}
                }
            }
        }
    }
}
