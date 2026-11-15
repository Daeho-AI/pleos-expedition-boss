package dev.pleos.entry.boss.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pleos.boss.game.*
import dev.pleos.entry.boss.BossGraph
import dev.pleos.entry.boss.BuildConfig
import kotlinx.coroutines.delay

private enum class BattlePage { ARRIVAL, ARENA, VICTORY, REWARD }

/** Presentation only. HP, drops and equipment always come from the persisted engine state. */
@Composable internal fun BossBattleScreen(
    game:GameState,graph:BossGraph,busy:Boolean,shownStrikeId:String?,onStrikeShown:(String)->Unit,
    navigate:(String)->Unit,modifier:Modifier=Modifier,
){
    BackHandler{navigate("home")}
    var page by remember{mutableStateOf(BattlePage.ARRIVAL)}
    var watched by remember{mutableStateOf(game.encounter)}
    var defeated by remember{mutableStateOf<Encounter?>(null)}
    var rewardId by remember{mutableStateOf<String?>(null)}
    var countdown by remember{mutableIntStateOf(8)}
    var inspecting by remember{mutableStateOf(false)}
    val latestGame by rememberUpdatedState(game)
    val chest=game.chests.find{it.id==rewardId}
    fun next(){watched=game.encounter;defeated=null;rewardId=null;inspecting=false;page=BattlePage.ARRIVAL}

    // A new persisted chest is the sole authority for the victory screen. Re-entry never replays old kills.
    LaunchedEffect(game.encounter?.id,page){
        if(page==BattlePage.ARRIVAL||page==BattlePage.ARENA){
            val before=watched;val current=game.encounter
            if(before!=null&&current!=null&&before.id!=current.id){
                val earned=game.chests.lastOrNull{it.encounterId==before.id}
                if(earned!=null){defeated=before;rewardId=earned.id;page=BattlePage.VICTORY}
                else watched=current
            }else if(before==null)watched=current
        }
    }
    LaunchedEffect(page){
        when(page){
            BattlePage.ARRIVAL->{delay(1900);page=BattlePage.ARENA}
            BattlePage.VICTORY->{delay(1800);page=BattlePage.REWARD}
            else->Unit
        }
    }
    LaunchedEffect(page,inspecting){
        if(page==BattlePage.REWARD&&!inspecting){
            countdown=8
            while(countdown>0){delay(1000);countdown--}
            watched=latestGame.encounter;defeated=null;rewardId=null;page=BattlePage.ARRIVAL
        }
    }
    val shown=defeated?:game.encounter
    val index=(shown?.bossIndex?:0).mod(graph.balance.bosses.size)
    // Keep the defeated enemy on the outgoing arena during the page transition.
    // The engine has already advanced, but the next enemy belongs to its own arrival page.
    val arenaEncounter=if(game.encounter?.id==watched?.id)game.encounter else watched?.copy(hpMilli=0)
    Box(modifier.background(Night).testTag("screen-battle")){
        AnimatedContent(targetState=page,transitionSpec={
            (fadeIn(tween(380))+slideInHorizontally(tween(480)){it/10}) togetherWith
                (fadeOut(tween(260))+scaleOut(tween(350),targetScale=.96f))
        },label="full screen encounter transition"){current->
            when(current){
                BattlePage.ARRIVAL->BossArrival(game,graph.balance,Modifier.fillMaxSize())
                BattlePage.ARENA->BattleScene(game.copy(encounter=arenaEncounter),graph.balance,shownStrikeId,onStrikeShown,Modifier.fillMaxSize())
                BattlePage.VICTORY->VictoryPage(index,graph.balance.bosses[index].name,Modifier.fillMaxSize())
                BattlePage.REWARD->RewardPage(game,graph,chest,busy,countdown,inspecting,
                    onOpen={inspecting=true;chest?.let{graph.openChest(it.id)}},
                    onEquip={chest?.let{graph.equip(it.templateId+":"+it.rarity.name)}},
                    onNext={next()},onGear={navigate("equipment")},modifier=Modifier.fillMaxSize())
            }
        }
        Row(Modifier.fillMaxWidth().padding(24.dp),verticalAlignment=Alignment.CenterVertically){
            FilledTonalButton(onClick={navigate("home")},modifier=Modifier.heightIn(min=64.dp).testTag("exit-battle"),shape=RoundedCornerShape(16.dp)){
                Icon(Icons.Default.ArrowBack,null,Modifier.size(21.dp));Spacer(Modifier.width(10.dp));Text("원정 기지",fontSize=16.sp)
            }
            Spacer(Modifier.weight(1f))
            BadgePill(if(BuildConfig.IS_FIXTURE)"PLEOS · 주차 / 시연 데이터" else "PLEOS · 주차 확인",Mint)
        }
        if(page==BattlePage.ARENA){
            Row(Modifier.align(Alignment.BottomEnd).padding(24.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                FilledTonalButton(onClick={graph.setEnabled(!game.hero.autoExpeditionEnabled)},modifier=Modifier.heightIn(min=64.dp).testTag("enable-expedition")){
                    Icon(if(game.hero.autoExpeditionEnabled)Icons.Default.Pause else Icons.Default.PlayArrow,null)
                    Spacer(Modifier.width(8.dp));Text(if(game.hero.autoExpeditionEnabled)"자동 사냥 중" else "사냥 재개")
                }
            }
        }
    }
}

@Composable private fun BossArrival(game:GameState,b:Balance,modifier:Modifier){
    val index=(game.encounter?.bossIndex?:0).mod(b.bosses.size)
    val tone=worlds[index]
    val reveal=remember{Animatable(0f)}
    LaunchedEffect(Unit){reveal.animateTo(1f,tween(1550,easing=FastOutSlowInEasing))}
    Box(modifier.testTag("battle-arrival")){
        ArenaBackdrop(index,Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color(0xA607101C)))
        Column(Modifier.align(Alignment.Center).widthIn(max=640.dp).fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Text("B O S S   E N C O U N T E R",color=tone.light,fontSize=16.sp,fontWeight=FontWeight.Bold)
            BossArt(index,Modifier.fillMaxWidth().height(280.dp))
            Text(b.bosses[index].name,color=Color.White,fontSize=46.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
            Spacer(Modifier.height(14.dp));Text(tone.name+"  /  "+(index+1).toString().padStart(2,'0')+"구역",color=Muted,fontSize=20.sp)
            Spacer(Modifier.height(32.dp));LinearProgressIndicator(progress=reveal.value,modifier=Modifier.width(250.dp).height(3.dp),color=tone.light,trackColor=Edge)
            Spacer(Modifier.height(16.dp));Text("원정 구역으로 진입합니다",color=Muted,fontSize=14.sp)
        }
    }
}

@Composable private fun VictoryPage(index:Int,name:String,modifier:Modifier){
    val sweep=remember{Animatable(0f)}
    LaunchedEffect(Unit){sweep.animateTo(1f,tween(1600))}
    Box(modifier.testTag("battle-victory")){
        ArenaBackdrop(index,Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color(0xC008141D)))
        Column(Modifier.align(Alignment.Center).padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Icon(Icons.Default.EmojiEvents,null,tint=Gold,modifier=Modifier.size(90.dp))
            Spacer(Modifier.height(20.dp));Text("VICTORY",fontSize=64.sp,fontWeight=FontWeight.Black,color=Gold,letterSpacing=7.sp)
            Spacer(Modifier.height(12.dp));Text(name+" 처치",fontSize=28.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(30.dp));Text("보상 상자 확보",color=Mint,fontSize=20.sp)
            Spacer(Modifier.height(20.dp));LinearProgressIndicator(progress=sweep.value,modifier=Modifier.width(220.dp).height(3.dp),color=Gold,trackColor=Edge)
        }
    }
}

@Composable private fun RewardPage(game:GameState,graph:BossGraph,chest:Chest?,busy:Boolean,countdown:Int,inspecting:Boolean,onOpen:()->Unit,onEquip:()->Unit,onNext:()->Unit,onGear:()->Unit,modifier:Modifier){
    val opened=chest?.openedAt!=null
    val template=chest?.let{graph.balance.template(it.templateId)}
    val equipped=chest!=null&&game.equipped[template!!.slot]==chest.templateId+":"+chest.rarity.name
    Box(modifier.background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFF20374A),Color(0xFF0C1623)))).testTag("battle-reward")){
        Column(Modifier.align(Alignment.Center).widthIn(max=680.dp).fillMaxWidth().padding(horizontal=32.dp,vertical=112.dp).verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally){
            Text(if(opened)"원정의 전리품" else "보스가 남긴 보물",fontSize=36.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(8.dp));Text(if((chest?.convertedDust?:0)>0)"중복 장비는 강화 재료가 됩니다" else if(opened)"획득한 장비로 다음 구역에 도전하세요" else "상자는 보관됩니다. 지금 열어 장비를 확인하세요",color=Muted,fontSize=17.sp,textAlign=TextAlign.Center)
            AnimatedContent(targetState=opened,transitionSpec={fadeIn(tween(450))+scaleIn(tween(550),initialScale=.65f) togetherWith fadeOut(tween(180))},label="chest reveals real item") { revealed->
                if(revealed&&chest!=null&&template!=null)EquipmentArt(Equipment(chest.templateId,chest.rarity),template.slot,Modifier.size(260.dp))
                else ChestArt(Gold,Modifier.size(260.dp))
            }
            if(opened&&chest!=null&&template!=null){
                BadgePill(rarityName(chest.rarity)+" · "+slotName(template.slot),rarityColor(chest.rarity))
                Spacer(Modifier.height(16.dp));Text(template.name,fontSize=30.sp,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(12.dp));Text("코인 +"+chest.coins+"    도감 조각 +1"+(if(chest.convertedDust>0)"    강화 가루 +"+chest.convertedDust else ""),color=Gold,fontSize=18.sp,textAlign=TextAlign.Center)
                Spacer(Modifier.height(10.dp));Text(comparison(game,graph.balance,chest.templateId+":"+chest.rarity.name,template.slot),color=Muted,fontSize=16.sp)
                Spacer(Modifier.height(26.dp));Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    ActionButton(if(equipped)"장착 완료" else "장착하기",onEquip,Modifier.weight(1f).testTag("battle-equip-reward"),!busy&&!equipped)
                    OutlinedButton(onClick=onGear,modifier=Modifier.weight(1f).heightIn(min=64.dp)){Text("장비 · 강화")}
                }
            }else{
                Spacer(Modifier.height(20.dp));ActionButton("상자 열기",onOpen,Modifier.fillMaxWidth().testTag("battle-open-reward"),!busy&&chest!=null)
            }
            Spacer(Modifier.height(14.dp));OutlinedButton(onClick=onNext,modifier=Modifier.fillMaxWidth().heightIn(min=64.dp).testTag("battle-next")){
                Text(if(inspecting)"다음 보스에 도전" else "다음 보스  ·  "+countdown+"초 후 자동 진입",fontSize=17.sp)
                Spacer(Modifier.width(12.dp));Icon(Icons.Default.ArrowForward,null)
            }
        }
    }
}
