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
import dev.pleos.boss.game.*
import dev.pleos.entry.boss.BossGraph

@Composable internal fun GearStudio(game:GameState,graph:BossGraph,busy:Boolean){
    var filter by remember{mutableStateOf<Slot?>(null)}
    var selectedKey by remember{mutableStateOf<String?>(null)}
    val selected=game.equipment.find{it.key==selectedKey}
    val template=selected?.let{graph.balance.template(it.templateId)}
    val preview=if(selected!=null&&template!=null)game.copy(equipped=game.equipped+(template.slot to selected.key)) else game
    val stats=graph.balance.stats(preview)
    Column(Modifier.fillMaxSize().testTag("screen-equipment")){
        Title("나만의 수호자","무기는 전투 동작을, 방어구와 장신구는 외형을 바꿉니다.")
        Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(22.dp)){
            Surface(Modifier.width(370.dp).fillMaxHeight(),shape=RoundedCornerShape(26.dp),color=Panel,border=BorderStroke(1.dp,Edge)){
                Column(Modifier.padding(22.dp).verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally){
                    Text(if(selected!=null)"장착 미리보기" else "현재 장착",color=Muted,fontSize=14.sp)
                    HeroPortrait(preview,Modifier.fillMaxWidth().height(300.dp).testTag("hero-preview"))
                    Text("여정의 수호자  Lv. "+game.hero.level,fontSize=23.sp,fontWeight=FontWeight.Bold)
                    Text(heroLook(preview).motion.title,color=heroLook(preview).accent,fontSize=15.sp)
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){Stat("공격력",stats.attack.toString());Stat("최대 체력",(stats.maxHpMilli/1000).toString())}
                    Spacer(Modifier.height(20.dp))
                    if(selected!=null&&template!=null){
                        val equipped=game.equipped[template.slot]==selected.key;val next=selected.upgradeLevel+1
                        BadgePill(rarityName(selected.rarity),rarityInk(selected.rarity))
                        Spacer(Modifier.height(9.dp));Text(template.name+" +"+selected.upgradeLevel,fontSize=24.sp,fontWeight=FontWeight.Bold)
                        Spacer(Modifier.height(8.dp));Text(if(equipped)"현재 장착한 장비입니다" else comparison(game,graph.balance,selected.key,template.slot),color=Mint,fontSize=14.sp)
                        Spacer(Modifier.height(16.dp));ActionButton(if(equipped)"장착 중" else "이 모습으로 장착",{graph.equip(selected.key)},Modifier.fillMaxWidth().testTag("equip-selected"),!busy&&!equipped)
                        OutlinedButton(onClick={graph.upgrade(selected.key)},enabled=!busy&&selected.upgradeLevel<10&&game.hero.coins>=next*30&&game.hero.dust>=next,modifier=Modifier.fillMaxWidth().padding(top=10.dp).heightIn(min=64.dp).testTag("upgrade-selected")){
                            Text(if(selected.upgradeLevel>=10)"최대 강화" else "강화  "+next*30+" 코인 · "+next+" 가루",fontSize=15.sp)
                        }
                        Text("+3 발광 · +5 빛 입자",color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=8.dp))
                    }else{
                        Slot.values().forEach{slot->val equipped=game.equipment.find{it.key==game.equipped[slot]}
                            Row(Modifier.fillMaxWidth().padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){Text(slotName(slot),color=Muted,modifier=Modifier.width(62.dp),fontSize=13.sp);Text(equipped?.let{graph.balance.template(it.templateId).name+" +"+it.upgradeLevel}?:"미장착",fontSize=16.sp)}
                        }
                        Text("오른쪽 장비를 눌러 외형을 미리 보세요.",color=Gold,fontSize=14.sp,modifier=Modifier.padding(top=20.dp))
                    }
                }
            }
            Column(Modifier.weight(1f)){
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    FilterChip(selected=filter==null,onClick={filter=null},label={Text("전체 "+game.equipment.size)},modifier=Modifier.heightIn(min=64.dp))
                    Slot.values().forEach{slot->FilterChip(selected=filter==slot,onClick={filter=slot},label={Text(slotName(slot))},modifier=Modifier.heightIn(min=64.dp).testTag("filter-"+slot.name))}
                }
                if(game.equipment.isEmpty())Text("보스 상자를 열면 첫 장비를 얻습니다.",color=Muted,modifier=Modifier.padding(24.dp))
                LazyVerticalGrid(columns=GridCells.Adaptive(210.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(14.dp),modifier=Modifier.weight(1f)){
                    items(game.equipment.filter{filter==null||graph.balance.template(it.templateId).slot==filter}.sortedWith(compareByDescending<Equipment>{it.rarity.ordinal}.thenBy{it.templateId}),key={it.key}){item->
                        val itemTemplate=graph.balance.template(item.templateId);val equipped=game.equipped[itemTemplate.slot]==item.key;val tint=rarityInk(item.rarity)
                        Surface(Modifier.clickable{selectedKey=item.key}.testTag("gear-"+item.key),color=if(selectedKey==item.key)tint.copy(alpha=.12f) else Panel,shape=RoundedCornerShape(20.dp),border=BorderStroke(if(selectedKey==item.key)2.dp else 1.dp,if(selectedKey==item.key)tint else Edge)){
                            Column(Modifier.padding(16.dp)){
                                Row(Modifier.fillMaxWidth()){Text(rarityName(item.rarity),color=tint,fontSize=12.sp);Spacer(Modifier.weight(1f));if(equipped)Text("장착",color=Mint,fontSize=12.sp)}
                                EquipmentArt(item,itemTemplate.slot,Modifier.fillMaxWidth().height(145.dp))
                                Text(itemTemplate.name,fontSize=19.sp,fontWeight=FontWeight.Bold)
                                Text("+"+item.upgradeLevel+"  ·  "+slotName(itemTemplate.slot),color=tint,fontSize=14.sp,modifier=Modifier.padding(top=7.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
