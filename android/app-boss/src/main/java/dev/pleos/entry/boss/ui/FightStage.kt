package dev.pleos.entry.boss.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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

/** Full-window fight. Effects follow stored HP deltas; drawing cannot grant damage or rewards. */
@Composable internal fun BattleScene(game:GameState,balance:Balance,shownStrikeId:String?,onStrikeShown:(String)->Unit,modifier:Modifier=Modifier){
    val infinite=rememberInfiniteTransition(label="arena idle")
    val clock by infinite.animateFloat(0f,60f,infiniteRepeatable(tween(60_000,easing=LinearEasing)),label="fight time")
    val attack=remember{Animatable(1f)}
    var previous by remember{mutableStateOf(game.encounter)}
    var damage by remember{mutableLongStateOf(0L)}
    var showReturn by remember{mutableStateOf(false)}
    val lastReturn=game.hero.progress.lastReturn
    LaunchedEffect(lastReturn?.id,lastReturn?.strikeDamageMilli){
        if(lastReturn!=null&&lastReturn.strikeDamageMilli>0&&shownStrikeId!=lastReturn.id){
            onStrikeShown(lastReturn.id);showReturn=true;delay(3200);showReturn=false
        }
    }
    val index=(game.encounter?.bossIndex?:0).mod(balance.bosses.size)
    val tone=worlds[index];val look=heroLook(game);val stats=balance.stats(game)
    val hp=((game.encounter?.hpMilli?:0).toFloat()/(game.encounter?.maxHpMilli?:1)).coerceIn(0f,1f)
    val smoothHp by animateFloatAsState(hp,tween(500),label="boss health")
    LaunchedEffect(game.encounter?.id,game.encounter?.hpMilli){
        val before=previous;val after=game.encounter;previous=after
        if(before!=null&&after!=null&&before.id==after.id&&game.hero.autoExpeditionEnabled){
            val delta=before.hpMilli-after.hpMilli
            if(delta>0){damage=delta;attack.snapTo(0f);attack.animateTo(1f,tween(880,easing=LinearEasing))}
        }
    }
    BoxWithConstraints(modifier.testTag("battle-scene")){
        val wide=maxWidth>=1100.dp
        Canvas(Modifier.fillMaxSize()){
            drawArenaWorld(index,clock)
            val p=attack.value
            val impact=if(p in .58f.. .99f)(1f-(p-.58f)/.41f) else 0f
            val u=maxOf(2f,floor(minOf(size.width/115f,size.height/148f)))
            val center=size.width*.5f
            val enemyFloor=size.height*.47f
            val heroFloor=size.height*.84f
            arenaPlatform(center,enemyFloor,26*u,tone.light,clock)
            arenaPlatform(center,heroFloor,24*u,look.accent,clock)
            // Large, readable silhouettes. Only energy attacks travel between the two positions.
            translate(center,enemyFloor){pixelMonster(index,clock,u*1.8f,impact)}
            translate(center,heroFloor){pixelHero(look,clock,u*1.18f,if(p<.25f)sin(p/.25f*PI).toFloat()*.3f else 0f)}
            if(damage>0&&p<.99f){
                val start=Offset(center,heroFloor-31*u);val end=Offset(center,enemyFloor-18*u)
                val flight=((p-.20f)/.40f).coerceIn(0f,1f)
                val pos=Offset(center,start.y+(end.y-start.y)*flight)
                val tint=look.accent
                if(p<.60f){
                    drawCircle(Brush.radialGradient(listOf(tint.copy(alpha=.50f),Color.Transparent),pos,11*u),11*u,pos)
                    for(i in 1..5)drawRoundRect(tint.copy(alpha=.25f/i),Offset(pos.x-u,pos.y+i*4*u),Size(u*2,u*4),CornerRadius(u))
                    when(look.motion){
                        WeaponMotion.BLADE->{for(i in -5..5)drawRect(tint,Offset(pos.x+i*u,pos.y+abs(i)*u),Size(2*u,2*u));drawRect(Color.White,Offset(pos.x-u,pos.y),Size(2*u,u))}
                        WeaponMotion.HAMMER->{drawPath(shape(pos.x,pos.y-5*u,pos.x+5*u,pos.y,pos.x,pos.y+5*u,pos.x-5*u,pos.y),tint);drawCircle(Color.White,1.7f*u,pos)}
                        WeaponMotion.SPEAR->{drawLine(tint,Offset(pos.x,pos.y+8*u),Offset(pos.x,pos.y-8*u),4*u,StrokeCap.Round);drawLine(Color.White,Offset(pos.x,pos.y+4*u),Offset(pos.x,pos.y-8*u),u)}
                        WeaponMotion.STAFF->{drawCircle(tint,4*u,pos);drawCircle(Color.White,2*u,pos);for(i in 0..3){val a=i*PI.toFloat()/2+clock*4;drawCircle(tint,1.2f*u,Offset(pos.x+cos(a)*7*u,pos.y+sin(a)*7*u))}}
                    }
                }
                if(impact>0){
                    drawCircle(Brush.radialGradient(listOf(tint.copy(alpha=impact*.7f),Color.Transparent),end,28*u),28*u,end)
                    drawCircle(tint.copy(alpha=impact),((1-impact)*25+2)*u,end,style=Stroke(2*u*impact))
                    for(i in 0..15){val a=i*PI.toFloat()/8;val r=(1-impact)*31*u
                        drawRect((if(i%3==0)Color.White else tint).copy(alpha=impact),Offset(end.x+cos(a)*r,end.y+sin(a)*r),Size(2*u,2*u))}
                }
            }
        }
        Column(Modifier.align(Alignment.TopCenter).padding(top=114.dp).widthIn(max=520.dp).fillMaxWidth(.65f),horizontalAlignment=Alignment.CenterHorizontally){
            Text("BOSS  "+(index+1).toString().padStart(2,'0')+"   /   "+tone.name,color=tone.light,fontSize=14.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(9.dp));Text(balance.bosses[index].name,fontSize=32.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(15.dp));LinearProgressIndicator(progress=smoothHp,modifier=Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(5.dp)),color=Gold,trackColor=Color.White.copy(alpha=.12f))
            Spacer(Modifier.height(7.dp));Text(String.format(Locale.KOREA,"%,.0f / %,.0f",(game.encounter?.hpMilli?:0)/1000.0,(game.encounter?.maxHpMilli?:0)/1000.0),color=Muted,fontSize=13.sp)
        }
        if(attack.value in .58f.. .98f&&damage>0)Text(String.format(Locale.KOREA,"−%.1f",damage/1000.0),color=Gold,fontSize=38.sp,fontWeight=FontWeight.Black,modifier=Modifier.align(Alignment.Center).offset(y=(-80).dp))
        if(wide){
            Column(Modifier.align(Alignment.CenterStart).padding(start=40.dp).width(235.dp)){
                Text("EXPEDITION",color=tone.light,fontSize=12.sp,letterSpacing=3.sp)
                Spacer(Modifier.height(12.dp));Text((game.encounter?.cycle?:1).toString().padStart(2,'0')+"번째 원정",fontSize=26.sp,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(26.dp));Text("보스 처치 → 상자 → 장비 성장",color=Muted,fontSize=15.sp)
                Spacer(Modifier.height(12.dp));Text("새 보상 상자  "+game.unopened.size+"개",color=Gold,fontSize=18.sp)
                Spacer(Modifier.height(30.dp));Text("자동 전투",color=Mint,fontSize=17.sp,fontWeight=FontWeight.Bold)
                Text("조작 없이 사냥이 이어집니다",color=Muted,fontSize=14.sp)
            }
            game.hero.progress.lastReturn?.takeIf{it.distanceChecked&&it.strikeDamageMilli>0}?.let{summary->
                Column(Modifier.align(Alignment.CenterEnd).padding(end=40.dp).width(235.dp)){
                    Text("여정의 힘",color=Gold,fontSize=22.sp,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(12.dp));Text(String.format(Locale.KOREA,"%.1f km",summary.creditedDistanceMeters/1000.0),fontSize=32.sp,fontWeight=FontWeight.Bold)
                    Text(String.format(Locale.KOREA,"필살기 피해 +%.0f",summary.strikeDamageMilli/1000.0),color=Gold,fontSize=18.sp)
                    Spacer(Modifier.height(10.dp));Text("복귀 정산에 반영된 기록",color=Muted,fontSize=13.sp)
                }
            }
        }
        if(showReturn&&lastReturn!=null){
            Surface(Modifier.align(Alignment.Center).padding(32.dp),color=Color(0xED101E2D),shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Gold.copy(alpha=.5f))){
                Column(Modifier.padding(horizontal=28.dp,vertical=20.dp),horizontalAlignment=Alignment.CenterHorizontally){
                    Text("여정의 힘 · 복귀 정산 완료",color=Gold,fontSize=24.sp,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(8.dp));Text(String.format(Locale.KOREA,"%.1f km → 필살기 피해 +%.0f",lastReturn.creditedDistanceMeters/1000.0,lastReturn.strikeDamageMilli/1000.0),fontSize=20.sp)
                }
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start=32.dp,bottom=30.dp)){
            Text("여정의 수호자  Lv. "+game.hero.level,fontSize=23.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(7.dp));Text(look.motion.title+"  ·  "+(look.weapon?.let{balance.template(it.templateId).name+" +"+it.upgradeLevel}?:"시작의 검"),color=look.accent,fontSize=14.sp)
            Spacer(Modifier.height(8.dp));Text("공격력 "+stats.attack+"  ·  "+(if(game.hero.autoExpeditionEnabled)"AUTO" else "PAUSED"),color=Muted,fontSize=13.sp)
        }
    }
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
