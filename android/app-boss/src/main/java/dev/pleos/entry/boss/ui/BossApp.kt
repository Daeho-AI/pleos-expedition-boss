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
import androidx.compose.ui.graphics.luminance
import androidx.activity.compose.BackHandler
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

@Composable internal fun rarityColor(r:Rarity):Color {
    val dark=MaterialTheme.colorScheme.background.luminance()<.5f
    return when(r){
        Rarity.COMMON->Muted
        Rarity.RARE->if(dark)Color(0xFFAFC9FF) else Color(0xFF315FA8)
        Rarity.EPIC->if(dark)Color(0xFFD4B9F5) else Color(0xFF7542A3)
        Rarity.LEGENDARY->Gold
    }
}
internal fun rarityName(r:Rarity)=when(r){Rarity.COMMON->"일반";Rarity.RARE->"희귀";Rarity.EPIC->"영웅";Rarity.LEGENDARY->"전설"}
internal fun slotName(s:Slot)=when(s){Slot.WEAPON->"무기";Slot.ARMOR->"방어구";Slot.CHARM->"장신구"}
internal fun slotIcon(s:Slot)=when(s){Slot.WEAPON->Icons.Default.FlashOn;Slot.ARMOR->Icons.Default.Shield;Slot.CHARM->Icons.Default.AutoAwesome}
private fun signed(n:Long)=if(n>0)"+"+n else n.toString()
internal fun comparison(game:GameState,b:Balance,key:String,slot:Slot):String {
    val before=b.stats(game)
    val after=b.stats(game.copy(equipped=game.equipped+(slot to key)))
    return if(after.attack==before.attack)"공격력 변화 없음" else "공격력 "+before.attack+" → "+after.attack+" ("+signed((after.attack-before.attack).toLong())+")"
}
private data class Tab(val id:String,val label:String,val icon:ImageVector)
private val tabs=listOf(Tab("home","원정",Icons.Default.Explore),Tab("equipment","장비",Icons.Default.Shield),Tab("chests","보상",Icons.Default.Inventory2),Tab("shop","상점",Icons.Default.Storefront),Tab("friends","친구",Icons.Default.People),Tab("collection","도감",Icons.Default.AutoAwesome))

@Composable fun BossApp(graph:BossGraph,requestPermissions:()->Unit){
    val game by graph.repository.state.collectAsStateWithLifecycle()
    val gateway by graph.gateway.state.collectAsStateWithLifecycle()
    val status by graph.status.collectAsStateWithLifecycle()
    val busy by graph.actionBusy.collectAsStateWithLifecycle()
    val playing by graph.fixtureRunning.collectAsStateWithLifecycle()
    val fatal by graph.fatalError.collectAsStateWithLifecycle()
    val foreground by graph.foreground.collectAsStateWithLifecycle()
    var screen by rememberSaveable{mutableStateOf("home")}
    var shownStrikeId by rememberSaveable{mutableStateOf<String?>(null)}
    val snackbar=remember{SnackbarHostState()}
    val now by produceState(SystemClock.elapsedRealtime(),foreground,gateway.ux){
        value=SystemClock.elapsedRealtime()
        while(foreground&&gateway.ux==UxMode.PARKED_INTERACTIVE){delay(500);value=SystemClock.elapsedRealtime()}
    }
    // SDK callbacks can be newer than the periodic UI tick. Never reject a fresh sample as future data.
    val ux=if(BuildConfig.IS_FIXTURE)gateway.ux else gateway.input(maxOf(now,SystemClock.elapsedRealtime())).ux
    LaunchedEffect(graph,ux,foreground){
        if(foreground&&ux==UxMode.PARKED_INTERACTIVE)graph.messages.collect{snackbar.showSnackbar(it)}
        else snackbar.currentSnackbarData?.dismiss()
    }
    BackHandler(enabled=screen!="home"&&screen!="battle"){
        screen=if(screen=="diagnostics"||screen=="history")"settings" else "home"
    }
    GameTheme {
        Scaffold(containerColor=Night,snackbarHost={if(foreground&&ux==UxMode.PARKED_INTERACTIVE)SnackbarHost(snackbar)}){padding->
            if(!foreground||ux!=UxMode.PARKED_INTERACTIVE){
                if(foreground&&ux==UxMode.UNKNOWN&&!BuildConfig.IS_FIXTURE&&gateway.setupAllowed)
                    SetupScreen(gateway,requestPermissions,graph::reconnect,graph::exportEvidence)
                else LimitedScreen(status.reason,playing,BuildConfig.IS_FIXTURE)
            }else if(screen=="battle"){
                GameTheme(dark=true){BossBattleScreen(game,graph,busy,shownStrikeId,{shownStrikeId=it},{screen=it},Modifier.fillMaxSize().padding(padding))}
            }else{
                BoxWithConstraints(Modifier.fillMaxSize().padding(padding)){
                    val wide=maxWidth>=960.dp
                    val compact=maxWidth<720.dp
                    Column(Modifier.fillMaxSize()){
                        Header(game,compact,screen,{screen=it})
                        HorizontalDivider(color=Edge)
                        if(!wide)Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=8.dp)){
                            tabs.forEach{NavItem(it,screen==it.id,if(it.id=="chests")game.unopened.size else 0){screen=it.id}}
                        }
                        Row(Modifier.weight(1f).fillMaxWidth()){
                            if(wide)Column(Modifier.width(96.dp).fillMaxHeight().padding(top=16.dp).verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){
                                tabs.forEach{NavItem(it,screen==it.id,if(it.id=="chests")game.unopened.size else 0){screen=it.id}}
                            }
                            Box(Modifier.weight(1f).fillMaxHeight().padding(if(wide)28.dp else 20.dp)){
                                when(screen){
                                    "home"->JourneyScreen(game,graph,{screen=it},playing)
                                    "chests"->ChestScreen(game,graph,busy)
                                    "equipment"->GearStudio(game,graph,busy)
                                    "shop"->ShopScreen(game,graph,busy)
                                    "friends"->FriendsScreen(game,graph)
                                    "collection"->CollectionScreen(game,graph.balance)
                                    "history"->HistoryScreen(game)
                                    "diagnostics"->DiagnosticsScreen(gateway,graph,fatal,now,requestPermissions)
                                    else->GameSettings(graph,playing,{screen=it})
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun NavItem(tab:Tab,selected:Boolean,count:Int,onClick:()->Unit){
    val color=if(selected)Ink else Muted
    Column(Modifier.size(88.dp,84.dp).clip(RoundedCornerShape(16.dp)).background(if(selected)MaterialTheme.colorScheme.surfaceVariant else Color.Transparent).clickable(onClick=onClick).testTag("nav-"+tab.id),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
        Box{Icon(tab.icon,null,tint=color,modifier=Modifier.size(26.dp));if(count>0)Box(Modifier.align(Alignment.TopEnd).offset(5.dp,(-3).dp).size(7.dp).background(Mint,androidx.compose.foundation.shape.CircleShape))}
        Spacer(Modifier.height(7.dp));Text(tab.label,color=color,fontSize=17.sp,fontWeight=if(selected)FontWeight.Bold else FontWeight.Normal)
    }
}
@Composable private fun Header(game:GameState,compact:Boolean,screen:String,navigate:(String)->Unit){
    Row(Modifier.fillMaxWidth().heightIn(min=88.dp).padding(horizontal=if(compact)16.dp else 28.dp),verticalAlignment=Alignment.CenterVertically){
        if(screen in listOf("settings","diagnostics","history"))IconButton(onClick={navigate(if(screen=="settings")"home" else "settings")},modifier=Modifier.size(64.dp).testTag("page-back")){Icon(Icons.Default.ArrowBack,"뒤로",tint=Ink)}
        Text("원정 보스",fontSize=if(compact)24.sp else 28.sp,fontWeight=FontWeight.Bold)

        Spacer(Modifier.weight(1f))
        Money(Icons.Default.Toll,"코인",game.hero.coins,compact)
        Spacer(Modifier.width(if(compact)16.dp else 30.dp))
        Money(Icons.Default.AutoAwesome,"가루",game.hero.dust,compact)
        Spacer(Modifier.width(12.dp))
        IconButton(onClick={navigate("settings")},modifier=Modifier.size(64.dp).testTag("nav-settings")){Icon(Icons.Default.Settings,"설정",tint=Muted,modifier=Modifier.size(26.dp))}
    }
}
@Composable private fun Money(icon:ImageVector,label:String,value:Int,compact:Boolean){
    Row(verticalAlignment=Alignment.CenterVertically){Icon(icon,label,tint=Muted,modifier=Modifier.size(22.dp));Spacer(Modifier.width(8.dp));Text(String.format(java.util.Locale.KOREA,"%,d",value),fontSize=if(compact)18.sp else 22.sp,fontWeight=FontWeight.SemiBold)}
}
@Composable private fun GameSettings(graph:BossGraph,playing:Boolean,navigate:(String)->Unit){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("screen-settings")){
        Title("설정","")
        SettingsRow("원정 기록",Icons.Default.History,Modifier.testTag("nav-history")){navigate("history")}
        SettingsRow("차량 연결",Icons.Default.SettingsInputAntenna,Modifier.testTag("nav-diagnostics")){navigate("diagnostics")}
        Spacer(Modifier.height(32.dp));Text("플레이 안내",fontSize=22.sp,fontWeight=FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp));Text("주행 중에는 게임이 멈춥니다. 주차 후 돌아오면 보상을 받고 자동 사냥을 이어갑니다.",color=Muted)
        Spacer(Modifier.height(12.dp));Text("이동거리에 따라 필살의 일격이 더해집니다. 한 번에 최대 30 km, 비운 시간은 최대 8시간까지 반영됩니다.",color=Muted)
        if(BuildConfig.IS_FIXTURE){
            Spacer(Modifier.height(32.dp));Text("시연 도구",fontSize=22.sp,fontWeight=FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp));Text("실제 차량 대신 가상 주행거리를 입력합니다.",color=Muted)
            Spacer(Modifier.height(16.dp));ActionButton("7분 · 10 km 이동 시연",{graph.simulateTrip();navigate("home")},Modifier.testTag("fixture-run"),!playing)
        }
        Spacer(Modifier.height(32.dp));Text("원정 보스",color=Muted,fontSize=16.sp)
    }
}
@Composable private fun SettingsRow(title:String,icon:ImageVector,modifier:Modifier=Modifier,onClick:()->Unit){
    Row(modifier.fillMaxWidth().heightIn(min=88.dp).clickable(onClick=onClick).padding(20.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Muted);Spacer(Modifier.width(20.dp));Text(title,Modifier.weight(1f),fontSize=22.sp);Icon(Icons.Default.ChevronRight,null,tint=Muted)}
    HorizontalDivider(color=Edge)
}
@Composable internal fun BadgePill(text:String,color:Color=Gold){Surface(color=color.copy(alpha=.1f),shape=RoundedCornerShape(24.dp),border=BorderStroke(1.dp,color.copy(alpha=.3f))){Text(text,color=color,fontSize=16.sp,modifier=Modifier.padding(horizontal=12.dp,vertical=6.dp))}}
@Composable internal fun CardBox(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){Surface(modifier,shape=RoundedCornerShape(18.dp),color=Panel){Column(Modifier.padding(24.dp),content=content)}}
@Composable internal fun ActionButton(text:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true){
    Button(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=64.dp),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=Ink,contentColor=Panel)){Text(text,fontSize=19.sp,fontWeight=FontWeight.SemiBold)}
}
@Composable internal fun Title(title:String,subtitle:String){
    Text(title,fontSize=30.sp,fontWeight=FontWeight.Bold)
    if(subtitle.isNotBlank()){Spacer(Modifier.height(8.dp));Text(subtitle,color=Muted,fontSize=18.sp)}
    Spacer(Modifier.height(24.dp))
}
@Composable internal fun Stat(label:String,value:String){Column{Text(label,color=Muted,fontSize=18.sp);Spacer(Modifier.height(6.dp));Text(value,fontSize=26.sp,fontWeight=FontWeight.SemiBold)}}

@Composable private fun CollectionScreen(game:GameState,b:Balance){
    Column(Modifier.fillMaxSize().testTag("screen-collection")){
        Title("도감","조각 10개를 모으면 미니어처를 얻습니다.")
        LazyVerticalGrid(columns=GridCells.Adaptive(280.dp),verticalArrangement=Arrangement.spacedBy(18.dp),horizontalArrangement=Arrangement.spacedBy(18.dp)){
            itemsIndexed(b.bosses){index,boss->
                val fragments=game.collection[boss.id]?:0
                val needed=b.chest.collectionUnlockFragments
                CardBox{
                    Row(Modifier.fillMaxWidth()){Text("No. "+(index+1).toString().padStart(2,'0'),color=Muted,fontSize=16.sp);Spacer(Modifier.weight(1f));Icon(if(fragments>=needed)Icons.Default.Star else Icons.Default.Lock,null,tint=if(fragments>=needed)Gold else Muted,modifier=Modifier.size(20.dp))}
                    BossArt(index,Modifier.fillMaxWidth().height(165.dp),muted=fragments==0)
                    Text(boss.name,fontSize=21.sp,fontWeight=FontWeight.SemiBold)
                    Spacer(Modifier.height(15.dp));LinearProgressIndicator(progress=(fragments/needed.toFloat()).coerceAtMost(1f),modifier=Modifier.fillMaxWidth().height(6.dp),color=Gold,trackColor=Edge)
                    Spacer(Modifier.height(9.dp));Text(if(fragments>=needed)"수집 완료" else "조각 "+fragments+" / "+needed,color=if(fragments>=needed)Gold else Muted,fontSize=18.sp)
                }
            }
        }
    }
}
@Composable private fun HistoryScreen(game:GameState){
    Column(Modifier.fillMaxSize().testTag("screen-history")){
        Title("원정 기록","")
        if(game.sessions.isEmpty())EmptyCard("첫 사냥을 기다리고 있어요","주차 상태에서 자동 사냥을 켜세요.",Icons.Default.Explore)
        LazyColumn(verticalArrangement=Arrangement.spacedBy(16.dp)){
            items(game.sessions.asReversed(),key={it.id}){trip->
                CardBox(Modifier.fillMaxWidth()){
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        Icon(Icons.Default.Explore,null,tint=Gold,modifier=Modifier.size(38.dp));Spacer(Modifier.width(20.dp))
                        Column(Modifier.weight(1f)){
                            Text(if(trip.state==SessionState.FINISHED)"완료한 원정" else "진행 중",fontSize=22.sp,fontWeight=FontWeight.Bold)
                            Text("사냥 시간 "+(trip.eligibleMs/60000)+"분 "+(trip.eligibleMs/1000%60)+"초",color=Muted,fontSize=18.sp)
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
        Title("차량 연결",if(BuildConfig.IS_FIXTURE)"가상 차량으로 실행 중입니다." else "차량에서 받은 정보를 확인합니다.")
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
            DiagnosticRow("Vehicle SDK 2.3.2",state.odometerStatus)
            DiagnosticRow("누적 주행거리",state.odometerMeters?.let{String.format("%.3f km",it/1000.0)}?:"미수신 · 기본 사냥 가능")
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
                OutlinedButton(onClick={graph.simulateTrip(10,10.0)},modifier=Modifier.heightIn(min=64.dp)){Text("10km 필살기 시연")}
                OutlinedButton(onClick={graph.simulateTrip(60,0.0)},modifier=Modifier.heightIn(min=64.dp)){Text("이동 없는 복귀 시연")}
            }
        }
    }
}
@Composable private fun DiagnosticRow(label:String,value:String){Row(Modifier.fillMaxWidth().padding(vertical=12.dp)){Text(label,color=Muted,modifier=Modifier.weight(1f));Text(value,modifier=Modifier.weight(1.5f),fontSize=15.sp)}}
@Composable private fun SetupScreen(state:GatewayState,request:()->Unit,reconnect:()->Unit,export:()->Unit){
    Box(Modifier.fillMaxSize().padding(48.dp),contentAlignment=Alignment.Center){
        CardBox(Modifier.widthIn(max=700.dp).fillMaxWidth()){
            Icon(Icons.Default.Shield,null,tint=Gold,modifier=Modifier.size(50.dp))
            Spacer(Modifier.height(22.dp));Text("주차 상태를 확인해 주세요",fontSize=32.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(16.dp));Text("차량 정보 접근을 허용하면 시작할 수 있습니다. 위치와 이동 경로는 수집하지 않습니다.",color=Muted)
            Spacer(Modifier.height(24.dp))
            DiagnosticRow("차량 속도 권한",if(state.speedPermission)"허용됨" else "필요")
            DiagnosticRow("기어 상태 권한",if(state.powertrainPermission)"허용됨" else "필요")
            DiagnosticRow("내비 연결",state.naviConnection)
            state.lastError?.let{Text(it,color=Color(0xFFEE9C91),fontSize=18.sp)}
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
            Text("주차 후 이어서 플레이하세요",fontSize=30.sp,fontWeight=FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp));Text("진행 상황은 저장되어 있습니다.",color=Muted,fontSize=20.sp)


        }
    }
}
