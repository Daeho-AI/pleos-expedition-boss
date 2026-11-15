package dev.pleos.entry.boss.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import kotlin.math.cos
import kotlin.math.sin

@Composable fun BossArt(index:Int,modifier:Modifier=Modifier,muted:Boolean=false){
    Canvas(modifier){
        val s=minOf(size.width/420f,size.height/380f)
        val tint=worlds[index%6].light
        drawCircle(Brush.radialGradient(listOf(tint.copy(alpha=.15f),Color.Transparent)),size.minDimension*.48f)
        withTransform({translate(size.width/2,size.height*.94f);scale(s,s,Offset.Zero)}){
            drawOval(Color.Black.copy(alpha=.25f),Offset(-140f,-6f),Size(280f,20f))
            pixelMonster(index,0f,10f)
        }
        if(muted)drawRect(Night.copy(alpha=.7f))
    }
}
@Composable fun ChestArt(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w=size.width;val h=size.height
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha=.22f),Color.Transparent)),size.minDimension/2)
        drawRoundRect(Color(0xFF775938),Offset(w*.19f,h*.42f),Size(w*.62f,h*.37f),CornerRadius(12f,12f))
        drawRoundRect(color,Offset(w*.17f,h*.3f),Size(w*.66f,h*.24f),CornerRadius(15f,15f))
        drawLine(Color(0xFF392F2B),Offset(w*.18f,h*.53f),Offset(w*.82f,h*.53f),5f)
        drawRect(color,Offset(w*.29f,h*.43f),Size(w*.06f,h*.36f))
        drawRect(color,Offset(w*.65f,h*.43f),Size(w*.06f,h*.36f))
        drawRoundRect(Color(0xFFFCEDC2),Offset(w*.43f,h*.47f),Size(w*.14f,h*.18f),CornerRadius(6f,6f))
        drawCircle(Color(0xFF775938),w*.025f,Offset(w*.5f,h*.55f))
    }
}
