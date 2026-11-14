package dev.pleos.entry.boss.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import dev.pleos.boss.game.*
import kotlin.math.*

@Composable internal fun LootReveal(item:Equipment,slot:Slot,modifier:Modifier=Modifier){
    val reveal=remember(item.key){Animatable(0f)}
    LaunchedEffect(item.key){reveal.animateTo(1f,tween(650,easing=FastOutSlowInEasing))}
    val infinite=rememberInfiniteTransition(label="treasure light")
    val angle by infinite.animateFloat(0f,360f,infiniteRepeatable(tween(14000,easing=LinearEasing)),label="treasure rays")
    Box(modifier){
        Canvas(Modifier.fillMaxSize()){
            val c=rarityInk(item.rarity)
            rotate(angle){for(i in 0..11){val a=i*PI.toFloat()/6;val r=size.minDimension*.49f
                drawPath(shape(center.x,center.y,center.x+cos(a)*r,center.y+sin(a)*r,center.x+cos(a+.12f)*r,center.y+sin(a+.12f)*r),c.copy(alpha=.10f*reveal.value))}}
        }
        EquipmentArt(item,slot,Modifier.fillMaxSize().graphicsLayer{val s=.5f+reveal.value*.5f;scaleX=s;scaleY=s;alpha=reveal.value;rotationZ=(1f-reveal.value)*-14})
    }
}
