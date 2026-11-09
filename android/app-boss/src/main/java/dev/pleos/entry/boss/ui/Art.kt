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

@Composable
fun BossArt(index: Int, modifier: Modifier = Modifier, muted: Boolean = false) {
    Canvas(modifier) {
        val scale = minOf(size.width / 500f, size.height / 350f)
        val origin = Offset(size.width / 2, size.height / 2)
        val tones = listOf(Color(0xFF72B897), Color(0xFFCFAC73), Color(0xFFCBB1D8), Color(0xFF8BADC5), Color(0xFF90CCDF), Color(0xFFA88BDB))
        val tone = tones[index % tones.size].copy(alpha = if (muted) .28f else 1f)
        drawCircle(Brush.radialGradient(listOf(tone.copy(alpha = .16f), Color.Transparent), center = origin, radius = size.minDimension * .6f), size.minDimension * .6f, origin)
        for (i in 0..25) {
            val x = size.width * ((i * 37 % 97) / 97f)
            val y = size.height * ((i * 23 % 79) / 79f)
            drawCircle(Color(0xFFEACB8C).copy(alpha = .15f + (i % 3) * .12f), if (i % 4 == 0) 2f else 1f, Offset(x, y))
        }
        withTransform({ translate(origin.x, origin.y + 10f * scale); scale(scale, scale, Offset.Zero) }) {
            drawOval(Color.Black.copy(alpha = .26f), Offset(-130f, 115f), Size(260f, 26f))
            drawOval(tone.copy(alpha = .13f), Offset(-155f, 111f), Size(310f, 42f), style = Stroke(2f))
            when (index % 6) {
                0 -> {
                    val body = Path().apply {
                        moveTo(-105f, 78f); lineTo(-91f, -45f); lineTo(-48f, -97f); lineTo(28f, -106f)
                        lineTo(90f, -49f); lineTo(112f, 71f); lineTo(72f, 117f); lineTo(-64f, 118f); close()
                    }
                    drawPath(body, Brush.linearGradient(listOf(tone, Color(0xFF244C48)), Offset(-80f,-90f),Offset(90f,110f)))
                    drawPath(Path().apply { moveTo(-75f,-55f);lineTo(-113f,-130f);lineTo(-32f,-85f);close() },tone)
                    drawPath(Path().apply { moveTo(42f,-86f);lineTo(102f,-136f);lineTo(85f,-45f);close() },tone)
                    drawPath(Path().apply { moveTo(-87f,32f);lineTo(-7f,5f);lineTo(79f,32f);lineTo(57f,100f);lineTo(-60f,97f);close() },Color(0xFF1C393B))
                    drawLine(tone.copy(alpha=.5f),Offset(-7f,5f),Offset(5f,111f),3f)
                    eyes(Offset(-42f,-18f),Offset(39f,-18f),Color(0xFFFFE1A1))
                    drawCircle(Color(0xFFD9C68F), 12f, Offset(-1f,58f))
                    drawCircle(Color(0xFF1A3132), 6f, Offset(-1f,58f))
                    for(i in 0..4) drawOval(tone,Offset(-75f+i*28f,-105f-(i%2)*12f),Size(34f,17f))
                }
                1 -> {
                    drawOval(tone.copy(alpha=.8f),Offset(-108f,53f),Size(70f,48f))
                    drawOval(tone.copy(alpha=.8f),Offset(44f,53f),Size(70f,48f))
                    drawOval(Brush.verticalGradient(listOf(tone,Color(0xFF6E593E)), -100f,100f),Offset(-122f,-83f),Size(244f,190f))
                    for(i in -1..1) drawPath(hexagon(i*62f,-4f,42f),Color(0xFF6E593E),style=Stroke(3f))
                    drawOval(tone,Offset(67f,-12f),Size(98f,84f)); drawCircle(Color(0xFF183039),7f,Offset(136f,17f))
                }
                2 -> {
                    drawOval(tone.copy(alpha=.6f),Offset(-142f,-30f),Size(130f,152f))
                    drawOval(tone.copy(alpha=.6f),Offset(12f,-30f),Size(130f,152f))
                    drawOval(tone,Offset(-86f,-111f),Size(172f,238f))
                    drawCircle(Color(0xFFEEE1CD),48f,Offset(-39f,-39f))
                    drawCircle(Color(0xFFEEE1CD),48f,Offset(39f,-39f))
                    drawCircle(Color(0xFF243044),25f,Offset(-39f,-39f))
                    drawCircle(Color(0xFF243044),25f,Offset(39f,-39f))
                    drawCircle(Color(0xFFEABD69),11f,Offset(-39f,-39f))
                    drawCircle(Color(0xFFEABD69),11f,Offset(39f,-39f))
                    drawPath(Path().apply{moveTo(-12f,6f);lineTo(12f,6f);lineTo(0f,31f);close()},Color(0xFFEABD69))
                    drawOval(Color(0xFFEBD9BB).copy(alpha=.55f),Offset(-46f,37f),Size(92f,74f))
                }
                3 -> {
                    drawRoundRect(tone,Offset(-122f,-64f),Size(242f,162f),CornerRadius(50f,50f))
                    drawRoundRect(Color(0xFF536878),Offset(-92f,65f),Size(39f,55f),CornerRadius(12f,12f))
                    drawRoundRect(Color(0xFF536878),Offset(52f,65f),Size(39f,55f),CornerRadius(12f,12f))
                    drawOval(Color(0xFFB6C7CF),Offset(-68f,-23f),Size(136f,114f))
                    drawOval(Color(0xFF536878),Offset(-35f,20f),Size(70f,42f))
                    eyes(Offset(-61f,-33f),Offset(62f,-33f),Color(0xFFEBC78D))
                    drawPath(Path().apply{moveTo(-72f,35f);lineTo(-100f,-5f);lineTo(-90f,60f);close()},Color(0xFFF5E4BD))
                    drawPath(Path().apply{moveTo(72f,35f);lineTo(100f,-5f);lineTo(90f,60f);close()},Color(0xFFF5E4BD))
                }
                4 -> {
                    drawOval(Brush.verticalGradient(listOf(tone,Color(0xFF3E7294)),-60f,100f),Offset(-134f,-63f),Size(258f,163f))
                    drawPath(Path().apply{moveTo(85f,17f);lineTo(181f,-30f);lineTo(160f,28f);lineTo(185f,72f);lineTo(86f,61f);close()},tone)
                    drawOval(Color(0xFFD0E5D7),Offset(-105f,44f),Size(149f,45f))
                    drawOval(tone.copy(alpha=.8f),Offset(-17f,55f),Size(85f,55f))
                    drawCircle(Color(0xFF173646),9f,Offset(-84f,1f))
                    drawCircle(Color(0xFFF8E7BE),3f,Offset(-87f,-3f))
                    drawArc(tone.copy(alpha=.7f),185f,160f,false,Offset(-41f,-139f),Size(60f,89f),style=Stroke(9f,cap=StrokeCap.Round))
                    drawCircle(tone.copy(alpha=.3f),12f,Offset(21f,-132f))
                }
                else -> {
                    drawPath(Path().apply{moveTo(-47f,20f);lineTo(-170f,-82f);lineTo(-137f,75f);lineTo(-65f,105f);close()},tone.copy(alpha=.5f))
                    drawPath(Path().apply{moveTo(47f,20f);lineTo(170f,-82f);lineTo(137f,75f);lineTo(65f,105f);close()},tone.copy(alpha=.5f))
                    drawOval(Brush.verticalGradient(listOf(tone,Color(0xFF423A74)),-100f,120f),Offset(-76f,-72f),Size(152f,198f))
                    drawPath(Path().apply{moveTo(-55f,-32f);lineTo(-72f,-132f);lineTo(-10f,-66f);close()},Color(0xFFE3C48D))
                    drawPath(Path().apply{moveTo(55f,-32f);lineTo(72f,-132f);lineTo(10f,-66f);close()},Color(0xFFE3C48D))
                    eyes(Offset(-35f,-14f),Offset(35f,-14f),Color(0xFFF2C778))
                    drawOval(Color(0xFFC7B7D8).copy(alpha=.55f),Offset(-36f,33f),Size(72f,78f))
                    drawCircle(Color(0xFFE3C48D),9f,Offset(0f,-73f))
                }
            }
        }
    }
}
private fun DrawScope.eyes(left: Offset,right: Offset,color: Color) {
    drawRoundRect(color,left-Offset(14f,4f),Size(28f,8f),CornerRadius(4f,4f))
    drawRoundRect(color,right-Offset(14f,4f),Size(28f,8f),CornerRadius(4f,4f))
}
private fun hexagon(x:Float,y:Float,r:Float)=Path().apply {
    for(i in 0..5){val a=Math.PI/3*i;val px=x+cos(a).toFloat()*r;val py=y+sin(a).toFloat()*r;if(i==0)moveTo(px,py)else lineTo(px,py)};close()
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
