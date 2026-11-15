package dev.pleos.entry.boss.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import kotlin.math.*

@Composable internal fun ArenaBackdrop(index:Int,modifier:Modifier){
    val infinite=rememberInfiniteTransition(label="arena atmosphere")
    val clock by infinite.animateFloat(0f,60f,infiniteRepeatable(tween(60_000,easing=LinearEasing)),label="arena clock")
    Canvas(modifier){drawArenaWorld(index,clock)}
}

/** A full-depth arena, not a widget background. Shapes remain crisp at both Pleos window sizes. */
internal fun DrawScope.drawArenaWorld(index:Int,t:Float){
    val tone=worlds[index.mod(worlds.size)];val w=size.width;val h=size.height
    val horizon=h*.36f
    drawRect(Brush.verticalGradient(listOf(Color(0xFF08121F),tone.sky,tone.land,Color(0xFF08151D))))
    drawCircle(Brush.radialGradient(listOf(tone.light.copy(alpha=.20f),Color.Transparent),Offset(w*.5f,h*.32f),h*.58f),h*.58f,Offset(w*.5f,h*.32f))
    // Ruined relay towers, far mountain ranges and a large, broken portal give the boss a location.
    for(layer in 0..2){
        val y=horizon+layer*h*.08f
        val path=Path().apply{moveTo(0f,h);lineTo(0f,y);for(i in 0..18)lineTo(i*w/16,y-(.04f+abs(sin(i*1.9f+layer))*.1f)*h);lineTo(w,h);close()}
        drawPath(path,tone.sky.copy(alpha=.4f+layer*.15f))
    }
    for(i in 0..11){
        val x=i*w/11;val height=h*(.07f+(i*29%11)*.007f)
        drawRect(Color(0xFF091724).copy(alpha=.6f),Offset(x,horizon-height),Size(w*.026f,height))
        drawRect(tone.light.copy(alpha=.17f),Offset(x+w*.008f,horizon-height+8),Size(3f,height*.7f))
    }
    val portal=Rect(w*.5f-h*.21f,h*.095f,w*.5f+h*.21f,h*.515f)
    drawArc(tone.light.copy(alpha=.10f),-154f,130f,false,portal.topLeft,portal.size,style=Stroke(h*.035f))
    drawArc(tone.light.copy(alpha=.35f),-154f,130f,false,portal.topLeft,portal.size,style=Stroke(2f))
    drawArc(tone.light.copy(alpha=.12f),28f,128f,false,portal.topLeft,portal.size,style=Stroke(h*.025f))
    drawArc(tone.light.copy(alpha=.35f),28f,128f,false,portal.topLeft,portal.size,style=Stroke(2f))
    // Perspective floor and inlaid energy rails converge behind the enemy.
    drawRect(Brush.verticalGradient(listOf(tone.land.copy(alpha=.30f),Color(0xFF08141E)),horizon,h),Offset(0f,horizon),Size(w,h-horizon))
    for(i in -5..5)drawLine(tone.light.copy(alpha=.07f),Offset(w*.5f+i*w*.04f,horizon),Offset(w*.5f+i*w*.21f,h),1.5f)
    for(i in 1..9){val y=horizon+(h-horizon)*(i/9f).pow(1.8f);drawLine(tone.light.copy(alpha=.07f),Offset(0f,y),Offset(w,y),1.5f)}
    for(side in listOf(-1,1)){
        val x=w*.5f+side*w*.28f
        drawLine(tone.light.copy(alpha=.13f),Offset(w*.5f+side*w*.12f,horizon),Offset(x,h),3f)
        for(i in 0..3){val yy=h*.46f+i*h*.15f;val xx=w*.5f+side*(w*.14f+i*w*.035f)
            drawRect(Color(0xFF0A1924),Offset(xx-12f,yy-h*.04f),Size(24f,h*.08f))
            drawRect(tone.light.copy(alpha=.65f),Offset(xx-5f,yy-h*.04f),Size(10f,5f))}
    }
    for(i in 0..43){val x=(i*197%997)/997f*w+sin(t*.4f+i)*8;val y=(i*109%971)/971f*h
        val a=.12f+(sin(t*1.8f+i)+1f)*.15f
        drawRect(tone.light.copy(alpha=a),Offset(x,y),Size(if(i%5==0)4f else 2f,if(i%5==0)4f else 2f))}
    // Dark foreground frames the actors without becoming a separate panel.
    drawRect(Brush.horizontalGradient(listOf(Color(0xC007111B),Color.Transparent,Color.Transparent,Color(0xC007111B))))
    drawRect(Brush.verticalGradient(listOf(Color(0xA006101A),Color.Transparent,Color.Transparent,Color(0xD006101A))))
}

internal fun DrawScope.arenaPlatform(x:Float,y:Float,r:Float,tint:Color,t:Float){
    drawOval(Color(0xFF061019),Offset(x-r,y-r*.18f),Size(r*2,r*.5f))
    drawOval(Brush.radialGradient(listOf(tint.copy(alpha=.10f),Color.Transparent),Offset(x,y),r),Offset(x-r,y-r*.24f),Size(r*2,r*.48f))
    drawOval(tint.copy(alpha=.38f),Offset(x-r,y-r*.24f),Size(r*2,r*.48f),style=Stroke(2f))
    drawOval(tint.copy(alpha=.17f),Offset(x-r*.77f,y-r*.18f),Size(r*1.54f,r*.36f),style=Stroke(1.5f))
    for(i in 0..7){val a=i*PI.toFloat()/4+t*.18f
        val pos=Offset(x+cos(a)*r*.88f,y+sin(a)*r*.20f)
        drawRect(tint.copy(alpha=.5f),pos,Size(4f,3f))}
}
