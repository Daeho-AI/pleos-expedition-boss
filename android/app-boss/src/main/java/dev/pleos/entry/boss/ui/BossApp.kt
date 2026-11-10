package dev.pleos.entry.boss.ui

import android.os.SystemClock
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pleos.boss.game.*
import dev.pleos.entry.boss.BossGraph
import dev.pleos.entry.boss.BuildConfig
import dev.pleos.entry.boss.sdk.GatewayState
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.roundToInt

internal val Night=Color(0xFF0B1220)
internal val Panel=Color(0xFF141F30)
internal val Edge=Color(0xFF293448)
internal val Gold=Color(0xFFE8BD73)
internal val Muted=Color(0xFF91A0B7)
internal val Mint=Color(0xFF82B9A4)
private val Ink=Color(0xFFEDF0F5)
private fun rarityColor(r:Rarity)=when(r){Rarity.COMMON->Color(0xFFA7B7C7);Rarity.RARE->Color(0xFF7CB9F0);Rarity.EPIC->Color(0xFFC197EF);Rarity.LEGENDARY->Gold}
private fun rarityName(r:Rarity)=when(r){Rarity.COMMON->"일반";Rarity.RARE->"희귀";Rarity.EPIC->"영웅";Rarity.LEGENDARY->"전설"}
private fun slotName(s:Slot)=when(s){Slot.WEAPON->"무기";Slot.ARMOR->"방어구";Slot.CHARM->"장신구"}
private fun slotIcon(s:Slot)=when(s){Slot.WEAPON->Icons.Default.FlashOn;Slot.ARMOR->Icons.Default.Shield;Slot.CHARM->Icons.Default.AutoAwesome}
private fun signed(n:Long)=if(n>0)"+"+n else n.toString()
private fun comparison(game:GameState,b:Balance,key:String,slot:Slot):String {
    val before=b.stats(game)
    val after=b.stats(game.copy(equipped=game.equipped+(slot to key)))
    return "장착하면  공격 "+signed((after.attack-before.attack).toLong())+" · 체력 "+signed((after.maxHpMilli-before.maxHpMilli)/1000)
}
private data class Tab(val id:String,val label:String,val icon:ImageVector)
private val tabs=listOf(Tab("home","원정",Icons.Default.Explore),Tab("chests","상자",Icons.Default.Inventory2),Tab("equipment","장비",Icons.Default.Shield),Tab("shop","상점",Icons.Default.Storefront),Tab("friends","친구·교환",Icons.Default.People),Tab("collection","도감",Icons.Default.AutoAwesome),Tab("history","기록",Icons.Default.History),Tab("diagnostics","연결",Icons.Default.SettingsInputAntenna))

@Composable fun BossApp(graph:BossGraph,requestPermissions:()->Unit){
    val game by graph.repository.state.collectAsStateWithLifecycle()
    val gateway by graph.gateway.state.collectAsStateWithLifecycle()
    val status by graph.status.collectAsStateWithLifecycle()
    val busy by graph.actionBusy.collectAsStateWithLifecycle()
    val playing by graph.fixtureRunning.collectAsStateWithLifecycle()
    val fatal by graph.fatalError.collectAsStateWithLifecycle()
    var screen by rememberSaveable{mutableStateOf("home")}
    val snackbar=remember{SnackbarHostState()}
    val now by produceState(SystemClock.elapsedRealtime()){while(true){delay(500);value=SystemClock.elapsedRealtime()}}
    val ux=if(BuildConfig.IS_FIXTURE)gateway.ux else gateway.input(now).ux
    LaunchedEffect(graph){graph.messages.collect{snackbar.showSnackbar(it)}}
    MaterialTheme(
        colorScheme=darkColorScheme(primary=Gold,onPrimary=Night,secondary=Mint,background=Night,surface=Panel,onSurface=Ink,onBackground=Ink,outline=Edge),
        typography=Typography(bodyLarge=androidx.compose.ui.text.TextStyle(fontSize=18.sp,lineHeight=28.sp),bodyMedium=androidx.compose.ui.text.TextStyle(fontSize=16.sp,lineHeight=24.sp)),
    ){
        Scaffold(containerColor=Night,snackbarHost={SnackbarHost(snackbar)}){padding->
            if(ux!=UxMode.PARKED_INTERACTIVE){
                if(!BuildConfig.IS_FIXTURE&&gateway.setupAllowed){
                    SetupScreen(gateway,requestPermissions,graph::reconnect,graph::exportEvidence)
                }else{
                    LimitedScreen(status.reason,playing,BuildConfig.IS_FIXTURE)
                }
            }else{
                BoxWithConstraints(Modifier.fillMaxSize().padding(padding)){
                    val wide=maxWidth>=850.dp
                    Column(Modifier.fillMaxSize()){
                        Header(game,BuildConfig.IS_FIXTURE)
                        if(!wide) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            tabs.forEach{tab->FilterChip(selected=screen==tab.id,onClick={screen=tab.id},label={Text(tab.label)},modifier=Modifier.heightIn(min=64.dp).testTag("nav-"+tab.id))}
                        }
                        Row(Modifier.weight(1f).fillMaxWidth()){
                            if(wide) Column(Modifier.width(104.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top=10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
                                tabs.forEach{tab->
                                    val selected=screen==tab.id
                                    Column(Modifier.size(88.dp,78.dp).clip(RoundedCornerShape(18.dp)).background(if(selected)Gold.copy(alpha=.12f)else Color.Transparent).clickable{screen=tab.id}.testTag("nav-"+tab.id),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                                        Icon(tab.icon,tab.label,tint=if(selected)Gold else Muted,modifier=Modifier.size(25.dp))
                                        Spacer(Modifier.height(7.dp))
                                        Text(tab.label+(if(tab.id=="chests"&&game.unopened.isNotEmpty())" "+game.unopened.size else ""),color=if(selected)Gold else Muted,fontSize=13.sp)
                                    }
                                }
                            }
                            Box(Modifier.weight(1f).fillMaxHeight().padding(if(wide)28.dp else 16.dp)){
                                when(screen){
                                    "home"->HomeScreen(game,graph,status,wide,{screen=it},playing)
                                    "chests"->ChestScreen(game,graph,busy)
                                    "equipment"->EquipmentScreen(game,graph,busy)
                                    "shop"->ShopScreen(game,graph,busy)
                                    "friends"->FriendsScreen(game,graph)
                                    "collection"->CollectionScreen(game,graph.balance)
                                    "history"->HistoryScreen(game)
                                    else->DiagnosticsScreen(gateway,graph,fatal,now,requestPermissions)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun Header(game:GameState,fixture:Boolean){
    Row(Modifier.fillMaxWidth().padding(horizontal=32.dp,vertical=22.dp),verticalAlignment=Alignment.CenterVertically){
        Icon(Icons.Default.AutoAwesome,null,tint=Gold,modifier=Modifier.size(30.dp))
        Spacer(Modifier.width(12.dp))
        Column{Text("원정 보스",fontSize=26.sp,fontWeight=FontWeight.Bold);Text("EVERY JOURNEY, A LITTLE STRONGER",fontSize=10.sp,letterSpacing=2.sp,color=Muted)}
        Spacer(Modifier.width(20.dp))
        BadgePill(if(fixture)"시연 데이터 · 차량 연결 아님" else "PLEOS · 차량 데이터",if(fixture)Gold else Mint)
        Spacer(Modifier.weight(1f))
        Money(Icons.Default.Toll,game.hero.coins.toString(),Gold)
        Spacer(Modifier.width(20.dp))
        Money(Icons.Default.AutoAwesome,game.hero.dust.toString(),Color(0xFFC197EF))
    }
}
@Composable private fun Money(icon:ImageVector,value:String,color:Color){Row(verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=color,modifier=Modifier.size(21.dp));Spacer(Modifier.width(7.dp));Text(value,fontSize=20.sp,fontWeight=FontWeight.SemiBold)}}
@Composable private fun BadgePill(text:String,color:Color=Gold){Surface(color=color.copy(alpha=.1f),shape=RoundedCornerShape(24.dp),border=BorderStroke(1.dp,color.copy(alpha=.3f))){Text(text,color=color,fontSize=12.sp,modifier=Modifier.padding(horizontal=13.dp,vertical=7.dp))}}
@Composable internal fun CardBox(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){Surface(modifier,shape=RoundedCornerShape(24.dp),color=Panel,border=BorderStroke(1.dp,Edge)){Column(Modifier.padding(24.dp),content=content)}}
@Composable internal fun ActionButton(text:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true){
    Button(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=64.dp),shape=RoundedCornerShape(16.dp)){Text(text,fontSize=17.sp,fontWeight=FontWeight.SemiBold)}
}
@Composable private fun Title(title:String,subtitle:String){
    Text(title,fontSize=30.sp,fontWeight=FontWeight.Bold)
    Spacer(Modifier.height(8.dp));Text(subtitle,color=Muted,fontSize=16.sp)
    Spacer(Modifier.height(24.dp))
}
@Composable private fun HomeScreen(game:GameState,graph:BossGraph,status:CombatStatus,wide:Boolean,navigate:(String)->Unit,playing:Boolean){
    val encounter=game.encounter
    val index=encounter?.bossIndex?:0
    val boss=graph.balance.bosses[index]
    val stats=graph.balance.stats(game)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("screen-home")){
        Row(verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text("다음 여정에도, 이어지는 모험",fontSize=30.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(9.dp));Text("지난 여정의 보스 체력과 성장은 그대로 남아 있어요.",color=Muted)}
            BadgePill("CHAPTER "+String.format("%02d",encounter?.cycle?:1),Mint)
        }
        Spacer(Modifier.height(26.dp))
        @Composable fun bossCard(modifier:Modifier){
            CardBox(modifier){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){BadgePill("BOSS "+(index+1).toString().padStart(2,'0'));Spacer(Modifier.weight(1f));Text("이번 운행 최대 2마리",color=Muted,fontSize=14.sp)}
                BossArt(index,Modifier.fillMaxWidth().height(if(wide)320.dp else 240.dp))
                Text(boss.name,fontSize=29.sp,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(7.dp));Text(listOf("오래된 숲의 첫 번째 수호자","모래 바람을 두른 느긋한 여행자","길 잃은 별을 모으는 밤의 파수꾼","묵직한 갑옷 속 작은 호기심","구름 사이를 유영하는 거대한 친구","모든 여정 끝에 기다리는 밤의 왕")[index],color=Muted,fontSize=15.sp)
                Spacer(Modifier.height(23.dp))
                Row(Modifier.fillMaxWidth()){Text("남은 체력",color=Muted,fontSize=14.sp);Spacer(Modifier.weight(1f));Text(((encounter?.hpMilli?:180000)/1000).toString()+" / "+((encounter?.maxHpMilli?:180000)/1000),color=Gold,fontWeight=FontWeight.SemiBold)}
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress=((encounter?.hpMilli?:180000).toFloat()/(encounter?.maxHpMilli?:180000)).coerceIn(0f,1f),modifier=Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(9.dp)),color=Mint,trackColor=Edge)
                Spacer(Modifier.height(18.dp));Text("처치 보상  ·  장비 상자 + 도감 조각",color=Gold,fontSize=14.sp)
            }
        }
        @Composable fun heroCards(modifier:Modifier){
            Column(modifier,verticalArrangement=Arrangement.spacedBy(18.dp)){
                CardBox(Modifier.fillMaxWidth()){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Surface(color=Gold.copy(alpha=.12f),shape=RoundedCornerShape(18.dp)){Icon(Icons.Default.Shield,null,tint=Gold,modifier=Modifier.padding(20.dp).size(36.dp))}
                        Spacer(Modifier.width(18.dp));Column{Text("여정의 수호자",color=Muted,fontSize=15.sp);Text("Lv. "+game.hero.level,fontSize=32.sp,fontWeight=FontWeight.Bold)}
                    }
                    Spacer(Modifier.height(22.dp))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Stat("공격력",stats.attack.toString());Stat("최대 체력",(stats.maxHpMilli/1000).toString());Stat("수집 장비",game.equipment.size.toString())}
                    Spacer(Modifier.height(22.dp))
                    val required=graph.balance.hero.xpToNextLevelMultiplier*game.hero.level
                    LinearProgressIndicator(progress=if(game.hero.level==graph.balance.hero.maxLevel)1f else game.hero.xp.toFloat()/required,modifier=Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),color=Gold,trackColor=Edge)
                    Spacer(Modifier.height(8.dp));Text(if(game.hero.level==graph.balance.hero.maxLevel)"최고 레벨 달성" else "다음 레벨  "+game.hero.xp+" / "+required+" XP",color=Muted,fontSize=13.sp)
                }
                CardBox(Modifier.fillMaxWidth()){
                    Row(verticalAlignment=Alignment.CenterVertically){ChestArt(Gold,Modifier.size(72.dp));Spacer(Modifier.width(14.dp));Column{Text("열지 않은 상자",color=Muted,fontSize=15.sp);Text(game.unopened.size.toString()+"개",fontSize=30.sp,fontWeight=FontWeight.Bold)}}
                    Spacer(Modifier.height(12.dp));ActionButton("보상 확인하기",{navigate("chests")},Modifier.fillMaxWidth(),game.unopened.isNotEmpty())
                }
                CardBox(Modifier.fillMaxWidth()){
                    Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.DirectionsCar,null,tint=Mint);Spacer(Modifier.width(10.dp));Text(if(game.hero.autoExpeditionEnabled)"자동 원정 켜짐" else "자동 원정 준비",fontWeight=FontWeight.SemiBold)}
                    Spacer(Modifier.height(12.dp));Text(if(game.hero.autoExpeditionEnabled)"유효한 주행 데이터가 들어오면 자동으로 기록합니다." else "목적지 안내를 따라 이동하면 전투가 계산돼요. 보상은 주차 후 확인하세요.",color=Muted,fontSize=15.sp)
                    Spacer(Modifier.height(17.dp));ActionButton(if(game.hero.autoExpeditionEnabled)"원정 기록 끄기" else "자동 원정 켜기",{graph.setEnabled(!game.hero.autoExpeditionEnabled)},Modifier.fillMaxWidth().testTag("enable-expedition"),!playing)
                }
            }
        }
        if(wide){Row(horizontalArrangement=Arrangement.spacedBy(24.dp)){bossCard(Modifier.weight(1.25f));heroCards(Modifier.weight(1f))}}
        else{bossCard(Modifier.fillMaxWidth());Spacer(Modifier.height(18.dp));heroCards(Modifier.fillMaxWidth())}
        if(BuildConfig.IS_FIXTURE){
            Spacer(Modifier.height(22.dp))
            CardBox(Modifier.fillMaxWidth()){
                Text("시연실",color=Gold,fontWeight=FontWeight.Bold,fontSize=17.sp)
                Text("가상 주행을 빠르게 재생합니다. 실제 차량 데이터나 실제 이동 기록이 아닙니다.",color=Muted,fontSize=14.sp)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    ActionButton("7분 가상 운행",{graph.simulateTrip()},Modifier.weight(1f).testTag("fixture-run"),!playing)
                    OutlinedButton(onClick={graph.simulateTrip(60)},enabled=!playing,modifier=Modifier.weight(1f).heightIn(min=64.dp),shape=RoundedCornerShape(16.dp)){Text("짧은 운행 · 1분")}
                }
            }
        }
        Spacer(Modifier.height(22.dp));Text(status.reason,color=Muted,fontSize=13.sp)
    }
}
@Composable private fun Stat(label:String,value:String){Column{Text(label,color=Muted,fontSize=13.sp);Spacer(Modifier.height(6.dp));Text(value,fontSize=24.sp,fontWeight=FontWeight.SemiBold)}}

@Composable private fun ChestScreen(game:GameState,graph:BossGraph,busy:Boolean){
    var selected by remember{mutableStateOf<String?>(null)}
    val revealed=game.chests.find{it.id==selected&&it.openedAt!=null}
    Column(Modifier.fillMaxSize().testTag("screen-chests")){
        Title("여정이 남긴 보물","보스가 남긴 장비, 코인, 도감 조각을 확인하세요.")
        if(game.unopened.isNotEmpty()){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Text("열지 않은 상자 "+game.unopened.size+"개",fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                ActionButton("모두 열기",{graph.openAll()},Modifier.testTag("open-all"),!busy)
            };Spacer(Modifier.height(20.dp))
        }
        LazyVerticalGrid(columns=GridCells.Adaptive(280.dp),verticalArrangement=Arrangement.spacedBy(18.dp),horizontalArrangement=Arrangement.spacedBy(18.dp)){
            if(game.chests.isEmpty())item(span={GridItemSpan(maxLineSpan)}){
                CardBox(Modifier.fillMaxWidth()){ChestArt(Gold,Modifier.align(Alignment.CenterHorizontally).size(180.dp));Text("첫 보물을 기다리고 있어요",fontSize=24.sp,modifier=Modifier.align(Alignment.CenterHorizontally));Spacer(Modifier.height(10.dp));Text("보스를 처치하면 상자가 여기에 저장됩니다.",color=Muted,modifier=Modifier.align(Alignment.CenterHorizontally))}
            }
            items(game.chests.asReversed(),key={it.id}){chest->
                CardBox{
                    if(chest.openedAt==null){
                        ChestArt(Gold,Modifier.fillMaxWidth().height(150.dp))
                        Text(graph.balance.bosses.find{it.id==chest.bossId}!!.name+"의 상자",fontSize=19.sp,fontWeight=FontWeight.Bold)
                        Spacer(Modifier.height(8.dp));Text("장비 1개 · 코인 · 도감 조각",color=Muted,fontSize=14.sp)
                        Spacer(Modifier.height(18.dp));ActionButton("상자 열기",{selected=chest.id;graph.openChest(chest.id)},Modifier.fillMaxWidth().testTag("open-chest-"+chest.id),!busy)
                    }else{
                        val template=graph.balance.template(chest.templateId)
                        BadgePill(rarityName(chest.rarity),rarityColor(chest.rarity))
                        Icon(slotIcon(template.slot),null,tint=rarityColor(chest.rarity),modifier=Modifier.padding(vertical=20.dp).size(54.dp))
                        Text(template.name,fontSize=21.sp,fontWeight=FontWeight.SemiBold)
                        Spacer(Modifier.height(9.dp));Text("코인 +"+chest.coins+" · 조각 +1",color=Muted,fontSize=14.sp)
                        if(chest.convertedDust>0)Text("중복 장비 → 강화 가루 +"+chest.convertedDust,color=rarityColor(chest.rarity),fontSize=13.sp)
                        Spacer(Modifier.height(15.dp));Text("획득 완료",color=Mint,fontSize=13.sp)
                    }
                }
            }
        }
    }
    if(revealed!=null)Dialog(onDismissRequest={selected=null}){
        CardBox(Modifier.widthIn(max=480.dp).fillMaxWidth()){
            val t=graph.balance.template(revealed.templateId)
            Text("새로운 보물",color=Gold,fontSize=16.sp,modifier=Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(20.dp))
            Surface(color=rarityColor(revealed.rarity).copy(alpha=.12f),shape=RoundedCornerShape(28.dp),modifier=Modifier.align(Alignment.CenterHorizontally)){
                Icon(slotIcon(t.slot),null,tint=rarityColor(revealed.rarity),modifier=Modifier.padding(40.dp).size(70.dp))
            }
            Spacer(Modifier.height(24.dp));Text(t.name,fontSize=28.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp));BadgePill(rarityName(revealed.rarity),rarityColor(revealed.rarity))
            Spacer(Modifier.height(18.dp));Text("코인 +"+revealed.coins+"   ·   도감 조각 +1",color=Muted)
            if(revealed.convertedDust>0)Text("중복 장비가 강화 가루 "+revealed.convertedDust+"개로 변환됐어요.",color=Muted)
            Spacer(Modifier.height(12.dp))
            Text(comparison(game,graph.balance,revealed.templateId+":"+revealed.rarity.name,t.slot),color=Mint,fontSize=16.sp)
            Spacer(Modifier.height(24.dp))
            ActionButton("장착하기",{graph.equip(revealed.templateId+":"+revealed.rarity.name);selected=null},Modifier.fillMaxWidth(),!busy)
            TextButton(onClick={selected=null},modifier=Modifier.align(Alignment.CenterHorizontally).heightIn(min=64.dp)){Text("나중에 확인")}
        }
    }
}
@Composable private fun EquipmentScreen(game:GameState,graph:BossGraph,busy:Boolean){
    var filter by remember{mutableStateOf<Slot?>(null)}
    Column(Modifier.fillMaxSize().testTag("screen-equipment")){
        Title("나만의 장비","같은 장비는 강화 재료로. 원하는 장비를 골라 성장시키세요.")
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            FilterChip(selected=filter==null,onClick={filter=null},label={Text("전체")},modifier=Modifier.heightIn(min=64.dp))
            Slot.values().forEach{slot->FilterChip(selected=filter==slot,onClick={filter=slot},label={Text(slotName(slot))},modifier=Modifier.heightIn(min=64.dp))}
        }
        Spacer(Modifier.height(20.dp))
        if(game.equipment.isEmpty())EmptyCard("아직 장비가 없어요","보스 상자를 열면 무기·방어구·장신구를 얻을 수 있어요.",Icons.Default.Shield)
        LazyVerticalGrid(columns=GridCells.Adaptive(310.dp),verticalArrangement=Arrangement.spacedBy(18.dp),horizontalArrangement=Arrangement.spacedBy(18.dp)){
            items(game.equipment.filter{filter==null||graph.balance.template(it.templateId).slot==filter}.sortedByDescending{it.rarity.ordinal},key={it.key}){item->
                val t=graph.balance.template(item.templateId)
                val equipped=game.equipped[t.slot]==item.key
                val multiplier=graph.balance.chest.rarityStatMultipliers.getValue(item.rarity)
                val attack=floor(t.baseAttack*multiplier).toInt()+if(t.slot!=Slot.ARMOR)item.upgradeLevel else 0
                val hp=floor(t.baseHp*multiplier).toInt()+if(t.slot==Slot.ARMOR)item.upgradeLevel*10 else 0
                val next=item.upgradeLevel+1
                CardBox{
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){BadgePill(rarityName(item.rarity),rarityColor(item.rarity));Spacer(Modifier.weight(1f));if(equipped)Text("장착 중",color=Mint,fontSize=13.sp)}
                    Icon(slotIcon(t.slot),null,tint=rarityColor(item.rarity),modifier=Modifier.padding(vertical=22.dp).size(52.dp))
                    Text(t.name+" +"+item.upgradeLevel,fontSize=22.sp,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(10.dp));Text("공격 +"+attack+"   체력 +"+hp,color=Muted,fontSize=16.sp)
                    if(!equipped){Spacer(Modifier.height(8.dp));Text(comparison(game,graph.balance,item.key,t.slot),color=Mint,fontSize=13.sp)}
                    Spacer(Modifier.height(20.dp))
                    ActionButton(if(equipped)"장착 중" else "장착하기",{graph.equip(item.key)},Modifier.fillMaxWidth(),!busy&&!equipped)
                    OutlinedButton(onClick={graph.upgrade(item.key)},enabled=!busy&&item.upgradeLevel<10&&game.hero.coins>=next*30&&game.hero.dust>=next,modifier=Modifier.fillMaxWidth().padding(top=10.dp).heightIn(min=64.dp),shape=RoundedCornerShape(16.dp)){
                        Text(if(item.upgradeLevel>=10)"최대 강화" else "강화 · "+(next*30)+" 코인 / "+next+" 가루",fontSize=14.sp)
                    }
                }
            }
        }
    }
}
@Composable private fun CollectionScreen(game:GameState,b:Balance){
    Column(Modifier.fillMaxSize().testTag("screen-collection")){
        Title("여정의 도감","보스 조각 10개를 모으면 작은 수호자가 컬렉션에 남아요.")
        LazyVerticalGrid(columns=GridCells.Adaptive(280.dp),verticalArrangement=Arrangement.spacedBy(18.dp),horizontalArrangement=Arrangement.spacedBy(18.dp)){
            itemsIndexed(b.bosses){index,boss->
                val fragments=game.collection[boss.id]?:0
                CardBox{
                    Row(Modifier.fillMaxWidth()){Text("No. "+(index+1).toString().padStart(2,'0'),color=Muted,fontSize=12.sp);Spacer(Modifier.weight(1f));Icon(if(fragments>=10)Icons.Default.Star else Icons.Default.Lock,null,tint=if(fragments>=10)Gold else Muted,modifier=Modifier.size(20.dp))}
                    BossArt(index,Modifier.fillMaxWidth().height(165.dp),muted=fragments==0)
                    Text(boss.name,fontSize=21.sp,fontWeight=FontWeight.SemiBold)
                    Spacer(Modifier.height(15.dp));LinearProgressIndicator(progress=(fragments/10f).coerceAtMost(1f),modifier=Modifier.fillMaxWidth().height(6.dp),color=Gold,trackColor=Edge)
                    Spacer(Modifier.height(9.dp));Text(if(fragments>=10)"미니어처 해금 · 조각 "+fragments+"개" else "조각 "+fragments+" / 10",color=if(fragments>=10)Gold else Muted,fontSize=14.sp)
                }
            }
        }
    }
}
@Composable private fun HistoryScreen(game:GameState){
    Column(Modifier.fillMaxSize().testTag("screen-history")){
        Title("차곡차곡 쌓인 여정","짧은 운행도 보스에게 남긴 피해는 사라지지 않아요.")
        if(game.sessions.isEmpty())EmptyCard("첫 여정을 기다리고 있어요","자동 원정을 켜면 안내 주행의 진행 상황이 기기에 저장됩니다.",Icons.Default.Explore)
        LazyColumn(verticalArrangement=Arrangement.spacedBy(16.dp)){
            items(game.sessions.asReversed(),key={it.id}){trip->
                CardBox(Modifier.fillMaxWidth()){
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        Icon(Icons.Default.Explore,null,tint=Gold,modifier=Modifier.size(38.dp));Spacer(Modifier.width(20.dp))
                        Column(Modifier.weight(1f)){
                            Text(if(trip.state==SessionState.FINISHED)"완료한 원정" else "이어지는 원정",fontSize=22.sp,fontWeight=FontWeight.Bold)
                            Text("유효 기록 "+(trip.eligibleMs/60000)+"분 "+(trip.eligibleMs/1000%60)+"초",color=Muted,fontSize=14.sp)
                        }
                        Stat("보스 처치",trip.kills.toString());Spacer(Modifier.width(35.dp));Stat("획득 상자",trip.chestIds.size.toString())
                    }
                }
            }
        }
    }
}
@Composable private fun EmptyCard(title:String,body:String,icon:ImageVector){CardBox(Modifier.fillMaxWidth()){Icon(icon,null,tint=Gold,modifier=Modifier.size(44.dp));Spacer(Modifier.height(20.dp));Text(title,fontSize=23.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));Text(body,color=Muted)}}
@Composable private fun DiagnosticsScreen(state:GatewayState,graph:BossGraph,fatal:String?,now:Long,requestPermissions:()->Unit){
    fun age(at:Long?)=if(at==null)"미수신" else((now-at).coerceAtLeast(0)/1000).toString()+"초 전"
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("screen-diagnostics")){
        Title("연결 확인",if(BuildConfig.IS_FIXTURE)"이 빌드는 시연용입니다. 숫자가 있어도 실제 SDK 연동을 뜻하지 않습니다." else "실제로 받은 값과 권한 상태를 확인합니다.")
        CardBox(Modifier.fillMaxWidth()){
            DiagnosticRow("실행 모드",state.source)
            DiagnosticRow("NaviHelper 3.0.5",state.naviConnection)
            DiagnosticRow("Android 차량 서비스",state.carConnection)
            DiagnosticRow("내비 상태",state.navStatus+" / "+state.naviMode)
            DiagnosticRow("차량 속도 권한",state.speedPermission.toString())
            DiagnosticRow("기어 상태 권한",state.powertrainPermission.toString())
            DiagnosticRow("속도 수신",(state.speedKph?.let{String.format("%.1f km/h",it)}?:"없음")+" · "+age(state.speedAtMs)+" · "+state.speedEvents+"회")
            DiagnosticRow("제한속도 수신",(state.limitKph?.toString()?:"없음")+" · "+age(state.limitAtMs)+" · "+state.limitEvents+"회")
            DiagnosticRow("안내 상태 수신",state.routeEvents.toString()+"회 · "+age(state.routeAtMs))
            DiagnosticRow("백그라운드 수신",state.receivedWhileBackground.toString()+"회")
            DiagnosticRow("주차·화면 상태",graph.ux().name)
            state.lastError?.let{Text(it,color=Color(0xFFEE9C91),modifier=Modifier.padding(top=12.dp))}
            fatal?.let{Text(it,color=Color(0xFFEE9C91),modifier=Modifier.padding(top=12.dp))}
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            ActionButton("다시 연결",graph::reconnect)
            OutlinedButton(onClick=requestPermissions,modifier=Modifier.heightIn(min=64.dp)){Text("권한 확인")}
            OutlinedButton(onClick=graph::exportEvidence,modifier=Modifier.heightIn(min=64.dp).testTag("export-evidence")){Text("확인 기록 저장")}
        }
        if(BuildConfig.IS_FIXTURE){
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                OutlinedButton(onClick={graph.simulateTrip(21,70.0)},modifier=Modifier.heightIn(min=64.dp)){Text("과속 반격 시연")}
                OutlinedButton(onClick={graph.simulateTrip(60,40.0,null)},modifier=Modifier.heightIn(min=64.dp)){Text("도로 정보 단절 시연")}
            }
        }
    }
}
@Composable private fun DiagnosticRow(label:String,value:String){Row(Modifier.fillMaxWidth().padding(vertical=12.dp)){Text(label,color=Muted,modifier=Modifier.weight(1f));Text(value,modifier=Modifier.weight(1.5f),fontSize=15.sp)}}
@Composable private fun SetupScreen(state:GatewayState,request:()->Unit,reconnect:()->Unit,export:()->Unit){
    Box(Modifier.fillMaxSize().padding(48.dp),contentAlignment=Alignment.Center){
        CardBox(Modifier.widthIn(max=700.dp).fillMaxWidth()){
            Icon(Icons.Default.Shield,null,tint=Gold,modifier=Modifier.size(50.dp))
            Spacer(Modifier.height(22.dp));Text("첫 원정을 준비해요",fontSize=32.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(16.dp));Text("차량 속도와 기어 상태를 읽어 자동 전투와 주차 화면을 구분합니다. 위치나 이동 경로는 저장하지 않습니다.",color=Muted)
            Spacer(Modifier.height(24.dp))
            DiagnosticRow("차량 속도 권한",if(state.speedPermission)"허용됨" else "필요")
            DiagnosticRow("기어 상태 권한",if(state.powertrainPermission)"허용됨" else "필요")
            DiagnosticRow("내비 연결",state.naviConnection)
            state.lastError?.let{Text(it,color=Color(0xFFEE9C91),fontSize=14.sp)}
            Spacer(Modifier.height(20.dp));ActionButton("차량 데이터 권한 확인",request,Modifier.fillMaxWidth().testTag("request-permissions"))
            Row{TextButton(onClick=reconnect,modifier=Modifier.heightIn(min=64.dp)){Text("다시 연결")};TextButton(onClick=export,modifier=Modifier.heightIn(min=64.dp)){Text("확인 기록 저장")}}
        }
    }
}
@Composable private fun LimitedScreen(reason:String,playing:Boolean,fixture:Boolean){
    Box(Modifier.fillMaxSize().background(Night).padding(40.dp).testTag("driving-limited"),contentAlignment=Alignment.Center){
        Column(horizontalAlignment=Alignment.CenterHorizontally){
            Icon(Icons.Default.DirectionsCar,null,tint=Mint,modifier=Modifier.size(48.dp))
            Spacer(Modifier.height(28.dp))
            Text(if(playing)"가상 운행 재생 중" else "원정 기록 상태",fontSize=30.sp,fontWeight=FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp));Text(if(playing)"시연 데이터 · 실제 주행 아님" else reason,color=Muted,fontSize=18.sp)
            Spacer(Modifier.height(24.dp));Text("보상과 장비는 주차 후 확인할 수 있어요.",color=Muted,fontSize=15.sp)
            if(fixture)Text("FIXTURE",color=Gold,modifier=Modifier.padding(top=20.dp),fontSize=12.sp)
        }
    }
}
