package dev.pleos.entry.boss.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pleos.boss.game.*
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.*

/** Observes actual combat results; animations never award damage or items. */
@Composable internal fun BattleScene(game:GameState,balance:Balance,shownStrikeId:String?,onStrikeShown:(String)->Unit,modifier:Modifier=Modifier){
    val infinite=rememberInfiniteTransition(label="world clock")
    val clock=infinite.animateFloat(0f,60f,infiniteRepeatable(tween(60_000,easing=LinearEasing)),label="world time")
    val attack=remember{Animatable(1f)};val ultimate=remember{Animatable(0f)}
    var previous by remember{mutableStateOf(game.encounter)}
    var damage by remember{mutableLongStateOf(0L)}
    var fallen by remember{mutableStateOf<Int?>(null)}
    var previousLevel by remember{mutableIntStateOf(game.hero.level)}
    var levelUp by remember{mutableStateOf(false)}
    var strike by remember{mutableStateOf<ReturnSummary?>(null)}
    val summary=game.hero.progress.lastReturn;val look=heroLook(game)
    // Removing the parked game tree also cancels this frame clock.

    LaunchedEffect(game.encounter?.id,game.encounter?.hpMilli){
        val before=previous;val after=game.encounter;previous=after
        if(before!=null&&after!=null&&game.hero.autoExpeditionEnabled&&strike==null){
            val defeated=before.id!=after.id;val delta=if(defeated)before.hpMilli else before.hpMilli-after.hpMilli
            if(delta>0){damage=delta;fallen=if(defeated)before.bossIndex else null;attack.snapTo(0f);attack.animateTo(1f,tween(880,easing=LinearEasing));fallen=null}
        }
    }
    LaunchedEffect(summary?.id,summary?.strikeDamageMilli){
        if(summary!=null&&summary.strikeDamageMilli>0&&shownStrikeId!=summary.id){onStrikeShown(summary.id);strike=summary
            try{ultimate.snapTo(0f);ultimate.animateTo(1f,tween(3200,easing=LinearEasing))}finally{strike=null}}
    }
    LaunchedEffect(game.hero.level){if(game.hero.level>previousLevel){previousLevel=game.hero.level;levelUp=true;delay(2800);levelUp=false}}
    val displayedIndex=strike?.strikeBossIndex?:fallen?:game.encounter?.bossIndex?:0
    Box(modifier.clip(RoundedCornerShape(28.dp)).testTag("battle-scene")){
        Canvas(Modifier.fillMaxSize()){
            val t=clock.value;val a=attack.value;val u=ultimate.value;val striking=strike!=null
            val phase=if(striking)((u-.18f)/.56f).coerceIn(0f,1f) else a
            val lunge=when{phase<.18f->-phase/.18f*.09f;phase<.43f->(phase-.18f)/.25f;phase<.60f->1f;else->(1f-phase)/.40f}.coerceIn(-.09f,1f)
            val swing=sin(phase*PI).toFloat().coerceAtLeast(0f)
            val impact=if(phase in .39f.. .82f)(1f-(phase-.39f)/.43f) else 0f
            val s=minOf(size.width/1200f,size.height/620f)*1.12f
            withTransform({scale(size.width/1200f,size.height/620f,Offset.Zero)}){drawWorld(displayedIndex,t,(size.width/1200f)/(size.height/620f))}
            val heroX=size.width*.27f;val bossX=size.width*.76f;val floor=size.height*.80f
            val travel=if(look.motion==WeaponMotion.STAFF&&!striking)0f else (bossX-heroX-170*s)*lunge
            val tint=if(striking)Color(0xFFFFD889) else look.accent
            if(striking){
                drawRect(Color(0xFF060E1B).copy(alpha=sin(u*PI).toFloat()*.5f))
                val radius=(90+u*310)*s
                drawCircle(tint.copy(alpha=(1-u)*.55f),radius,Offset(heroX,floor-125*s),style=Stroke(3*s))
                for(i in 0..11){val angle=i*PI.toFloat()/6+t*.7f;drawLine(tint.copy(alpha=(1-u)*.4f),Offset(heroX+cos(angle)*radius,floor-125*s+sin(angle)*radius),Offset(heroX+cos(angle)*(radius+24*s),floor-125*s+sin(angle)*(radius+24*s)),3*s)}
            }
            withTransform({translate(bossX,floor);scale(s,s,Offset.Zero)}){
                drawOval(Color.Black.copy(alpha=.32f),Offset(-160f,-8f),Size(330f,27f))
                drawMonster(displayedIndex,t,impact,if(phase>.77f)sin((phase-.77f)/.23f*PI).toFloat()*.7f else 0f,if(fallen!=null)impact*.4f else 0f)
            }
            if(travel>20*s)for(i in 3 downTo 1){withTransform({translate(heroX+travel-i*22*s,floor);scale(s,s,Offset.Zero)}){drawPath(shape(-46f,-210f,34f,-205f,44f,-45f,-58f,-43f),tint.copy(alpha=.045f*(4-i)))}}
            withTransform({translate(heroX+travel,floor);scale(s,s,Offset.Zero)}){
                drawOval(Color.Black.copy(alpha=.35f),Offset(-90f,-8f),Size(175f,25f))
                rotate(if(look.motion==WeaponMotion.SPEAR)lunge*12 else lunge*5,Offset(0f,-100f)){drawAdventurer(look,t,swing,if(phase>.8f)sin(phase*PI).toFloat() else 0f,if(striking)sin(u*PI).toFloat() else 0f)}
            }
            if(phase in .24f.. .89f&&(damage>0||striking)){
                val ix=bossX-60*s;val iy=floor-150*s
                when(if(striking)WeaponMotion.BLADE else look.motion){
                    WeaponMotion.BLADE->{val radius=(if(striking)200f else 150f)*s
                        drawArc(tint.copy(alpha=impact*.7f),-150f+phase*85,130f,false,Offset(ix-radius,iy-radius),Size(radius*2,radius*2),style=Stroke((if(striking)24f else 12f)*s,cap=StrokeCap.Round))
                        drawArc(Color.White.copy(alpha=impact),-137f+phase*85,91f,false,Offset(ix-radius*.9f,iy-radius*.9f),Size(radius*1.8f,radius*1.8f),style=Stroke(3*s,cap=StrokeCap.Round))}
                    WeaponMotion.HAMMER->{drawOval(tint.copy(alpha=impact*.3f),Offset(ix-170*s,floor-48*s),Size(340*s,92*s),style=Stroke((3+impact*5)*s))
                        for(i in 0..8){val r=(1-impact)*120*s;val angle=i*2.4f;drawRect(tint.copy(alpha=impact),Offset(ix+cos(angle)*r,floor-abs(sin(angle))*r),Size(7*s,11*s))}}
                    WeaponMotion.SPEAR->{drawLine(tint.copy(alpha=impact*.65f),Offset(heroX+travel+40*s,iy),Offset(ix+130*s,iy-30*s),20*s,StrokeCap.Round);drawLine(Color.White.copy(alpha=impact),Offset(heroX+travel+40*s,iy),Offset(ix+130*s,iy-30*s),3*s,StrokeCap.Round)}
                    WeaponMotion.STAFF->{val projectile=((phase-.24f)/.28f).coerceIn(0f,1f)
                        val center=Offset(heroX+80*s+(ix-heroX-80*s)*projectile,iy-35*s-sin(projectile*PI).toFloat()*55*s)
                        drawCircle(Brush.radialGradient(listOf(Color.White,tint,tint.copy(alpha=.02f)),center,42*s),42*s,center)
                        for(i in 1..5)drawCircle(tint.copy(alpha=.35f/i),12*s/i,center-Offset(i*20*s,0f))}
                }
                if(impact>0){drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha=impact*.8f),tint.copy(alpha=impact*.3f),Color.Transparent),Offset(ix,iy),125*s),125*s,Offset(ix,iy))
                    for(i in 0..13){val angle=i*2.399f;val r=(1-impact)*150*s;val o=Offset(ix+cos(angle)*r,iy+sin(angle)*r);drawLine(tint.copy(alpha=impact),o,o+Offset(cos(angle)*19*s,sin(angle)*19*s),3*s,StrokeCap.Round)}}
            }
            drawRect(Brush.verticalGradient(listOf(Color(0xCC071510),Color.Transparent),0f,size.height*.27f),size=Size(size.width,size.height*.3f))
        }
        Row(Modifier.fillMaxWidth().padding(26.dp),verticalAlignment=Alignment.Top){
            Column(Modifier.weight(1f)){
                Text("CHAPTER "+(game.encounter?.cycle?:1).toString().padStart(2,'0')+"  /  "+worlds[displayedIndex].name,color=Color(0xFFC0D9CF),fontSize=14.sp,letterSpacing=1.sp)
                Spacer(Modifier.height(5.dp));Text(balance.bosses[displayedIndex].name,color=Color.White,fontSize=28.sp,fontWeight=FontWeight.Bold)
            }
            Column(Modifier.width(260.dp),horizontalAlignment=Alignment.End){
                Text(if(strike!=null)"이동의 힘을 해방합니다" else "BOSS  "+((game.encounter?.hpMilli?:0)/1000)+" / "+((game.encounter?.maxHpMilli?:1)/1000),color=Gold,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress=if(strike!=null)(1f-ultimate.value).coerceIn(0f,1f) else ((game.encounter?.hpMilli?:0).toFloat()/(game.encounter?.maxHpMilli?:1)).coerceIn(0f,1f),modifier=Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(8.dp)),color=Gold,trackColor=Color.White.copy(alpha=.13f))
            }
        }
        if(attack.value in .40f.. .95f&&strike==null&&damage>0)Text(String.format(Locale.KOREA,"−%.1f",damage/1000.0),color=Color(0xFFFFE4A1),fontSize=38.sp,fontWeight=FontWeight.Black,modifier=Modifier.align(Alignment.CenterEnd).padding(end=104.dp,bottom=((attack.value-.4f)*60).dp))
        if(fallen!=null&&strike==null)Column(Modifier.align(Alignment.Center).background(Night.copy(alpha=.8f),RoundedCornerShape(20.dp)).padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("BOSS CLEAR",color=Gold,fontSize=30.sp,fontWeight=FontWeight.Black);Text("보물 상자 +1",color=Color.White,fontSize=20.sp)}
        if(levelUp)Text("LEVEL UP  ·  Lv. "+game.hero.level,color=Gold,fontSize=29.sp,fontWeight=FontWeight.Black,modifier=Modifier.align(Alignment.CenterStart).padding(38.dp))
        Column(Modifier.align(Alignment.BottomStart).padding(26.dp)){
            Text("여정의 수호자  Lv. "+game.hero.level,color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold)
            Text(look.motion.title+"  ·  "+(look.weapon?.let{balance.template(it.templateId).name+" +"+it.upgradeLevel}?:"시작의 검"),color=look.accent,fontSize=14.sp)
        }
        Text(if(game.hero.autoExpeditionEnabled)"●  AUTO BATTLE" else "Ⅱ  사냥 대기",color=if(game.hero.autoExpeditionEnabled)Mint else Muted,fontSize=14.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp,modifier=Modifier.align(Alignment.BottomEnd).padding(26.dp))
        strike?.let{captured->Column(Modifier.align(Alignment.TopCenter).padding(top=112.dp).background(Color(0xDD101C23),RoundedCornerShape(22.dp)).padding(horizontal=30.dp,vertical=17.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Text("필살의 일격",color=Gold,fontSize=34.sp,fontWeight=FontWeight.Black)
            Text(String.format(Locale.KOREA,"%.1f km의 힘  ·  %.1f 피해",captured.creditedDistanceMeters/1000.0,captured.strikeDamageMilli/1000.0),color=Color.White,fontSize=19.sp)
        }}
    }
}

@Composable internal fun ReturnCard(summary:ReturnSummary,awaitingDistance:Boolean){
    Surface(Modifier.fillMaxWidth().testTag("return-summary"),shape=RoundedCornerShape(18.dp),color=Mint.copy(alpha=.08f)){
        Row(Modifier.padding(horizontal=22.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text("복귀 정산 완료",color=Mint,fontSize=19.sp,fontWeight=FontWeight.Bold);Text("비운 시간 "+summary.creditedMs/60_000+"분 · 보스 "+summary.bosses+"마리 · 상자 "+summary.chests+"개",color=Muted,fontSize=15.sp)}
            Text(when{awaitingDistance->"이동거리 확인 중";summary.strikeDamageMilli>0->String.format(Locale.KOREA,"필살의 일격  %.1f km · %.1f 피해",summary.creditedDistanceMeters/1000.0,summary.strikeDamageMilli/1000.0);else->"기본 보상 정산"},color=Gold,fontSize=16.sp)
        }
    }
}
