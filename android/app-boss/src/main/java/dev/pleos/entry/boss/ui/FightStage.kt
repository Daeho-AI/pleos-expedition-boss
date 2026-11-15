package dev.pleos.entry.boss.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

/** Stationary vertical encounter: observes real HP changes and never awards progress. */
@Composable internal fun BattleScene(game:GameState,balance:Balance,shownStrikeId:String?,onStrikeShown:(String)->Unit,modifier:Modifier=Modifier){
    val infinite=rememberInfiniteTransition(label="pixel idle")
    val clock=infinite.animateFloat(0f,60f,infiniteRepeatable(tween(60_000,easing=LinearEasing)),label="pixel time")
    val attack=remember{Animatable(1f)};val ultimate=remember{Animatable(0f)}
    var previous by remember{mutableStateOf(game.encounter)}
    var damage by remember{mutableLongStateOf(0L)}
    var fallen by remember{mutableStateOf<Int?>(null)}
    var previousLevel by remember{mutableIntStateOf(game.hero.level)}
    var levelUp by remember{mutableStateOf(false)}
    var strike by remember{mutableStateOf<ReturnSummary?>(null)}
    val summary=game.hero.progress.lastReturn;val look=heroLook(game)
    LaunchedEffect(game.encounter?.id,game.encounter?.hpMilli){
        val before=previous;val after=game.encounter;previous=after
        if(before!=null&&after!=null&&game.hero.autoExpeditionEnabled&&strike==null){
            val defeated=before.id!=after.id;val delta=if(defeated)before.hpMilli else before.hpMilli-after.hpMilli
            if(delta>0){damage=delta;fallen=if(defeated)before.bossIndex else null;attack.snapTo(0f);attack.animateTo(1f,tween(900,easing=LinearEasing));fallen=null}
        }
    }
    LaunchedEffect(summary?.id,summary?.strikeDamageMilli){
        if(summary!=null&&summary.strikeDamageMilli>0&&shownStrikeId!=summary.id){onStrikeShown(summary.id);strike=summary
            try{ultimate.snapTo(0f);ultimate.animateTo(1f,tween(2800,easing=LinearEasing))}finally{strike=null}}
    }
    LaunchedEffect(game.hero.level){if(game.hero.level>previousLevel){previousLevel=game.hero.level;levelUp=true;delay(2200);levelUp=false}}
    val index=(strike?.strikeBossIndex?:fallen?:game.encounter?.bossIndex?:0).mod(balance.bosses.size)
    Box(modifier.clip(RoundedCornerShape(22.dp)).background(Color(0xFF101B30)).testTag("battle-scene")){
        Canvas(Modifier.fillMaxSize()){
            val t=clock.value;val striking=strike!=null
            val p=if(striking)ultimate.value else attack.value
            val impact=if(p in .62f.. .97f)(1f-(p-.62f)/.35f) else 0f
            val tone=worlds[index];val tint=if(striking)Gold else look.accent
            val unit=maxOf(2f,floor(minOf(size.width/105f,size.height/145f)))
            val center=floor(size.width/2/unit)*unit
            val enemyFloor=floor(size.height*.43f/unit)*unit
            val heroFloor=floor(size.height*.88f/unit)*unit
            drawRect(Brush.verticalGradient(listOf(Color(0xFF15243C),Color(0xFF121C2F),Color(0xFF203040))))
            for(i in 0..25){val x=((i*47+17)%(size.width.toInt().coerceAtLeast(1))).toFloat();val y=74f+(i*29)%(size.height.toInt().coerceAtLeast(150)-130)
                drawRect(tone.light.copy(alpha=.12f+(i%3)*.05f),Offset(floor(x/unit)*unit,floor(y/unit)*unit),Size(unit,unit))}
            fun dais(y:Float,width:Float){
                drawRect(Color(0xFF091525),Offset(center-width/2,y),Size(width,4*unit))
                drawRect(Color(0xFF2C4960),Offset(center-width/2+4*unit,y-unit),Size(width-8*unit,2*unit))
                drawRect(tint.copy(alpha=.4f),Offset(center-width/2+8*unit,y-unit),Size(width-16*unit,unit))
            }
            dais(enemyFloor,43*unit);dais(heroFloor,39*unit)
            translate(center,enemyFloor){pixelMonster(index,t,unit*1.25f,impact)}
            translate(center,heroFloor){pixelHero(look,t,unit,if(striking)sin(p*PI).toFloat() else 0f)}
            // Only projectiles travel. The character remains on the lower platform.
            if(p in .08f.. .98f&&(damage>0||striking)){
                val startY=heroFloor-29*unit;val endY=enemyFloor-11*unit
                val flight=((p-.25f)/.40f).coerceIn(0f,1f)
                val yy=floor((startY+(endY-startY)*flight)/unit)*unit
                val radius=if(striking)5 else 2
                if(p<.65f){
                    val reach=if(p<.25f)sin(p/.25f*PI/2).toFloat() else 1f
                    for(i in 1..4)drawRect(tint.copy(alpha=.30f/i),Offset(center-unit,yy+i*4*unit),Size(2*unit,3*unit))
                    when(look.motion){
                        WeaponMotion.BLADE->{for(i in -3..3)drawRect(tint.copy(alpha=reach),Offset(center+i*unit,yy+abs(i)*unit),Size(unit*2,unit*2));drawRect(Color.White,Offset(center-unit,yy),Size(unit*2,unit))}
                        WeaponMotion.HAMMER->{for(x in -radius..radius)for(y in -radius..radius)if(abs(x)+abs(y)<=radius+1)drawRect(if(x==0&&y==0)Color.White else tint.copy(alpha=reach),Offset(center+x*unit,yy+y*unit),Size(unit,unit))}
                        WeaponMotion.SPEAR->{drawRect(tint,Offset(center-unit,yy-unit*5),Size(unit*2,unit*10));drawRect(Color.White,Offset(center,yy-unit*5),Size(unit,unit*6))}
                        WeaponMotion.STAFF->{for(x in -radius..radius)for(y in -radius..radius)if(x*x+y*y<=radius*radius+1)drawRect(if(abs(x)+abs(y)<2)Color.White else tint.copy(alpha=reach),Offset(center+x*unit,yy+y*unit),Size(unit,unit))}
                    }
                    if(striking)for(x in -2..2)drawRect(Gold.copy(alpha=.5f),Offset(center+x*unit*4,yy+unit*6),Size(unit*2,unit*4))
                }
                if(impact>0)for(i in 0..11){val a=i*PI.toFloat()/6;val r=(1-impact)*23*unit
                    drawRect(tint.copy(alpha=impact),Offset(floor((center+cos(a)*r)/unit)*unit,floor((endY+sin(a)*r)/unit)*unit),Size(unit*2,unit*2))}
            }
        }
        Column(Modifier.fillMaxWidth().padding(18.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){Text("BOSS  "+(game.encounter?.cycle?:1).toString().padStart(2,'0'),fontSize=11.sp,color=Muted,modifier=Modifier.weight(1f));Text(if(game.hero.autoExpeditionEnabled)"● AUTO" else "Ⅱ PAUSED",color=Mint,fontSize=11.sp,fontWeight=FontWeight.Bold)}
            Spacer(Modifier.height(7.dp));Text(balance.bosses[index].name,fontSize=18.sp,fontWeight=FontWeight.Bold,color=Color.White)
            Spacer(Modifier.height(9.dp));LinearProgressIndicator(progress=((game.encounter?.hpMilli?:0).toFloat()/(game.encounter?.maxHpMilli?:1)).coerceIn(0f,1f),modifier=Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)),color=Gold,trackColor=Color.White.copy(alpha=.1f))
        }
        if(attack.value in .64f.. .98f&&strike==null&&damage>0)Text(String.format(Locale.KOREA,"−%.1f",damage/1000.0),color=Gold,fontSize=24.sp,fontWeight=FontWeight.Bold,modifier=Modifier.align(Alignment.TopCenter).padding(top=88.dp))
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom=13.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Text("여정의 수호자  Lv. "+game.hero.level,color=Color.White,fontSize=13.sp,fontWeight=FontWeight.SemiBold)
            Text(look.motion.title+"  ·  "+(look.weapon?.let{balance.template(it.templateId).name+" +"+it.upgradeLevel}?:"시작의 검"),color=look.accent,fontSize=10.sp)
        }
        if(fallen!=null&&strike==null)PixelNotice("보스 처치","보상 상자 +1",Modifier.align(Alignment.Center))
        if(levelUp)PixelNotice("LEVEL UP","Lv. "+game.hero.level,Modifier.align(Alignment.Center))
        strike?.let{PixelNotice("필살의 일격",String.format(Locale.KOREA,"%.1f km  ·  %.0f 피해",it.creditedDistanceMeters/1000.0,it.strikeDamageMilli/1000.0),Modifier.align(Alignment.Center))}
    }
}
@Composable private fun PixelNotice(title:String,detail:String,modifier:Modifier){
    Column(modifier.background(Color(0xE6101A2A),RoundedCornerShape(12.dp)).padding(horizontal=16.dp,vertical=13.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(title,color=Gold,fontSize=20.sp,fontWeight=FontWeight.Bold);Text(detail,color=Color.White,fontSize=13.sp)}
}
@Composable internal fun ReturnCard(summary:ReturnSummary,awaitingDistance:Boolean){
    Surface(Modifier.fillMaxWidth().testTag("return-summary"),shape=RoundedCornerShape(18.dp),color=Mint.copy(alpha=.07f)){
        Column(Modifier.padding(20.dp)){
            Text("복귀 정산 완료",color=Mint,fontSize=19.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(6.dp));Text("비운 시간 "+summary.creditedMs/60_000+"분 · 보스 "+summary.bosses+"마리 · 상자 "+summary.chests+"개",color=Muted,fontSize=15.sp)
            Spacer(Modifier.height(8.dp));Text(when{awaitingDistance->"이동거리 확인 중";summary.strikeDamageMilli>0->String.format(Locale.KOREA,"필살의 일격  %.1f km · %.1f 피해",summary.creditedDistanceMeters/1000.0,summary.strikeDamageMilli/1000.0);else->"기본 보상 정산"},color=Gold,fontSize=16.sp)
        }
    }
}
