package dev.pleos.entry.boss.ui

import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import kotlin.math.*

internal data class WorldTone(val sky: Color,val haze: Color,val land: Color,val light: Color,val name: String)
internal val worlds=listOf(
    WorldTone(Color(0xFF102E2D),Color(0xFF3F7065),Color(0xFF1D3E32),Color(0xFFB1EAC4),"잠든 숲의 문"),
    WorldTone(Color(0xFF3B303C),Color(0xFFBA8B64),Color(0xFF70513E),Color(0xFFFFD598),"황금 모래 계곡"),
    WorldTone(Color(0xFF171F3B),Color(0xFF645B89),Color(0xFF2F344E),Color(0xFFD8C3FF),"등불이 머무는 밤"),
    WorldTone(Color(0xFF26343D),Color(0xFF687B79),Color(0xFF303E3A),Color(0xFFF4BC7D),"잊힌 철갑 요새"),
    WorldTone(Color(0xFF244C66),Color(0xFF90B9C1),Color(0xFF496A6E),Color(0xFFD4F4E5),"구름 위의 항로"),
    WorldTone(Color(0xFF241D3B),Color(0xFF65517A),Color(0xFF352E47),Color(0xFFCAB0FF),"별의 끝 성채")
)

/** World coordinates are 1200 x 620. Static geometry is intentionally deterministic. */
internal fun DrawScope.drawWorld(index: Int,t: Float,stretch:Float=1f) {
    val p=worlds[index%6]
    drawRect(Brush.verticalGradient(listOf(p.sky,p.haze,p.land),0f,620f),size=Size(1200f,620f))
    drawCircle(Brush.radialGradient(listOf(p.light.copy(alpha=.28f),Color.Transparent),Offset(770f,155f),340f),340f,Offset(770f,155f))
    withTransform({translate(800f,111f);scale(1f/stretch,1f,Offset.Zero)}){
        drawCircle(p.light.copy(alpha=.55f),49f,Offset.Zero)
        drawCircle(p.sky.copy(alpha=.55f),47f,Offset(17f,-12f))
    }
    for(layer in 0..2){val y=270f+layer*70;val path=Path().apply{moveTo(0f,620f);lineTo(0f,y);for(i in 0..12){val x=i*110f;lineTo(x,y-60f-sin(i*1.7f+layer)*62f)};lineTo(1200f,620f);close()};drawPath(path,p.sky.copy(alpha=.25f+layer*.12f))}
    // Ruins behind the combatants establish scale and depth.
    for(i in 0..7){val x=80f+i*155f;val h=65f+(i*39%100);drawRect(p.sky.copy(alpha=.35f),Offset(x,410f-h),Size(33f,h));drawRect(p.haze.copy(alpha=.2f),Offset(x-8f,408f-h),Size(49f,12f));drawRect(p.light.copy(alpha=.09f),Offset(x+4f,423f-h),Size(4f,h-20f))}
    if(index==0||index==2){
        for(i in 0..7){val x=i*190f-75;val yy=210f+(i%3)*35
            drawPath(shape(x,440f,x+20f,80f,x+44f,65f,x+39f,440f),p.sky.copy(alpha=.65f))
            for(k in 0..3)drawPath(shape(x-67f+k*8,yy+k*46,x+25f,yy-105f+k*46,x+120f-k*8,yy+k*46),p.sky.copy(alpha=.7f))
        }
        drawPath(shape(0f,0f,350f,0f,84f,430f,0f,490f),p.light.copy(alpha=.04f))
        drawPath(shape(500f,0f,600f,0f,360f,430f,320f,430f),p.light.copy(alpha=.06f))
    }
    if(index==4)for(i in 0..7){val x=(i*198f+t*4)%1500-150;val y=170f+(i%3)*67;drawOval(Color(0xFFE3F0E2).copy(alpha=.12f),Offset(x,y),Size(260f,53f))}
    drawOval(p.light.copy(alpha=.06f),Offset(70f,375f),Size(1050f,170f))
    drawPath(shape(0f,493f,90f,474f,340f,486f,590f,476f,910f,486f,1200f,469f,1200f,620f,0f,620f),Brush.verticalGradient(listOf(p.land,p.sky),475f,620f))
    drawLine(p.light.copy(alpha=.2f),Offset(0f,493f),Offset(1200f,486f),3f)
    for(i in 0..41){val x=(i*127%1200).toFloat();val y=510f+(i*43%94);val r=3f+i%7
        drawOval(p.sky.copy(alpha=.45f),Offset(x,y),Size(r*3,r));drawLine(p.light.copy(alpha=.10f),Offset(x,y),Offset(x+r*2,y),1.3f)
    }
    for(i in 0..29){val x=(i*173%1200).toFloat()+sin(t*.3f+i)*8;val y=90f+(i*89%370)+sin(t*.8f+i*3)*12
        val a=(.12f+(sin(t*1.2f+i)+1f)*.17f).coerceIn(0f,1f)
        drawCircle(p.light.copy(alpha=a),if(i%4==0)2.5f else 1.1f,Offset(x,y))
    }
    // Foreground leaves frame the scene without covering the fighters.
    for(side in 0..1){val x=if(side==0)0f else 1200f;val direction=if(side==0)1f else -1f
        for(i in 0..5){val sway=sin(t*.8f+i)*5f;drawPath(Path().apply{moveTo(x,620f);quadraticBezierTo(x+direction*(i*20+20),510f,x+direction*(i*35+35+sway),482f);quadraticBezierTo(x+direction*(i*22+75),548f,x+direction*55,620f);close()},p.sky.copy(alpha=.7f))}
    }
}

internal fun DrawScope.drawMonster(index: Int,t: Float,hit: Float=0f,attack: Float=0f,defeat: Float=0f) {
    val p=worlds[index%6];val bob=sin(t*2.2f)*5
    val skin=when(index%6){0->Color(0xFF568D70);1->Color(0xFFCEAA74);2->Color(0xFFB1A0CE);3->Color(0xFF7C969E);4->Color(0xFF86CDDC);else->Color(0xFF9B7AC0)}
    val ink=Color(0xFF152A29);val eye=Color(0xFFFFE3A4)
    withTransform({translate(-attack*38f+sin(hit*32)*hit*9f,bob+defeat*60);scale(1f+hit*.06f,1f-hit*.05f,Offset(0f,-100f))}){
        when(index%6){
            0->{
                // A full-bodied forest sentinel: roots, bark armour, hands and antlers.
                for(side in listOf(-1,1)){val x=side*70f;rotate(side*(4+sin(t*2)*3),Offset(x,-74f)){plate(skin,x-24,-88f,48f,77f,20f);outlined(shape(x-36,-20f,x+29,-20f,x+40,3f,x-42,3f),ink)}
                    rotate(side*(10+sin(t*2+.6f)*7)-attack*side*35,Offset(side*94f,-190f)){plate(skin,side*109f-30,-196f,60f,125f,23f);for(k in 0..2)drawLine(eye.copy(alpha=.6f),Offset(side*109f-18+k*18,-82f),Offset(side*109f-20+k*18,-64f),6f,StrokeCap.Round)} }
                outlined(shape(-90f,-226f,84f,-226f,108f,-104f,62f,-54f,-70f,-54f,-109f,-112f),skin)
                drawPath(shape(-69f,-208f,-13f,-179f,-28f,-76f,-78f,-93f),Color(0xFF244F41))
                drawPath(shape(9f,-210f,74f,-195f,82f,-105f,19f,-79f),Color(0xFF73A77C))
                drawCircle(Brush.radialGradient(listOf(eye,Color(0xFF79DBA3),Color.Transparent),Offset(0f,-136f),35f),35f,Offset(0f,-136f));drawCircle(eye,9f,Offset(0f,-136f))
                rotate(attack*-14,Offset(0f,-233f)){plate(Color(0xFF6A9F7D),-67f,-293f,135f,99f,24f);drawPath(shape(-56f,-261f,-22f,-266f,-20f,-250f,-52f,-246f),eye);drawPath(shape(14f,-267f,48f,-260f,45f,-246f,14f,-251f),eye);drawPath(shape(-30f,-226f,31f,-226f,18f,-210f,-20f,-210f),ink)
                    for(side in listOf(-1,1)){drawPath(shape(side*40f,-284f,side*72f,-336f,side*75f,-300f,side*108f,-316f,side*76f,-272f),Color(0xFFA9C38A));drawOval(Color(0xFF91BA79),Offset(side*53f-22,-307f),Size(45f,18f))}}
            }
            1->{
                for(i in 0..3)plate(Color(0xFF947746),-105f+i*66,-48f+sin(t*2+i)*3,40f,45f,17f)
                drawOval(Brush.verticalGradient(listOf(Color(0xFFE4C68C),Color(0xFF806344)),-215f,-30f),Offset(-128f,-215f),Size(273f,199f))
                for(i in -1..1)for(j in 0..1){val x=i*64f+j*25;val y=-153f+j*65;drawPath(Path().apply{for(k in 0..5){val a=k*PI/3;val xx=x+cos(a).toFloat()*42;val yy=y+sin(a).toFloat()*35;if(k==0)moveTo(xx,yy)else lineTo(xx,yy)};close()},Color(0xFF6F5C42),style=Stroke(4f))}
                translate(-attack*20f,0f){drawOval(skin,Offset(-190f,-122f),Size(116f,93f));drawCircle(ink,9f,Offset(-164f,-94f));drawCircle(Color.White,3f,Offset(-167f,-97f));drawLine(ink,Offset(-188f,-66f),Offset(-149f,-63f),3f,StrokeCap.Round)}
                for(i in 0..4)drawPath(shape(-93f+i*48,-197f,-77f+i*48,-223f,-57f+i*48,-197f),Color(0xFFB19666))
            }
            2->{
                for(side in listOf(-1,1))rotate(side*(sin(t*2.7f)*12+attack*28),Offset(side*60f,-150f)){outlined(shape(side*61f,-204f,side*165f,-141f,side*122f,-43f,side*63f,-67f),Color(0xFF7F74A3));for(i in 0..3)drawLine(skin.copy(alpha=.6f),Offset(side*(77f+i*14),-156f),Offset(side*(83f+i*10),-76f),5f,StrokeCap.Round)}
                drawOval(Brush.verticalGradient(listOf(skin,Color(0xFF6A608D)),-230f,0f),Offset(-89f,-237f),Size(178f,226f));drawOval(Color(0xFFD9C7B2),Offset(-48f,-102f),Size(96f,80f))
                for(side in listOf(-1,1)){drawCircle(Color(0xFFF1DFC1),49f,Offset(side*38f,-173f));drawCircle(ink,29f,Offset(side*38f-4,-173f));drawCircle(eye,13f,Offset(side*38f-6,-173f));drawCircle(Color.White,5f,Offset(side*38f-11,-180f));drawPath(shape(side*23f,-226f,side*72f,-271f,side*70f,-208f),skin);drawLine(eye,Offset(side*30f,-21f),Offset(side*34f,1f),9f,StrokeCap.Round)}
                outlined(shape(-15f,-137f,14f,-137f,-4f,-108f),Color(0xFFEFC47C))
            }
            3->{
                for(i in 0..3)plate(Color(0xFF405865),-100f+i*65,-67f,37f,68f,12f)
                drawOval(Color(0xFF4F6872),Offset(-117f,-173f),Size(274f,144f))
                for(i in 0..3){val x=-58f+i*48;outlined(shape(x,-175f,x+33,-184f,x+54,-115f,x+13,-91f,x-13,-134f),skin);drawLine(Color(0xFFC7DDD9),Offset(x+4,-171f),Offset(x+30,-158f),3f)}
                rotate(attack*-13f,Offset(-80f,-106f)){drawOval(skin,Offset(-188f,-160f),Size(135f,121f));outlined(shape(-166f,-137f,-180f,-198f,-132f,-162f),Color(0xFF657A8B));drawOval(Color(0xFF52676E),Offset(-213f,-94f),Size(66f,48f));drawCircle(ink,6f,Offset(-195f,-72f));drawCircle(ink,6f,Offset(-177f,-72f));drawPath(shape(-176f,-55f,-182f,-103f,-152f,-64f),Color(0xFFFFE2A3));drawCircle(eye,7f,Offset(-151f,-121f));drawLine(ink,Offset(-166f,-131f),Offset(-140f,-136f),6f,StrokeCap.Round)}
                drawPath(Path().apply{moveTo(134f,-133f);cubicTo(174f,-155f,187f,-113f,160f,-108f)},skin,style=Stroke(8f,cap=StrokeCap.Round))
            }
            4->{
                translate(0f,-34f+sin(t*1.5f)*12){rotate(sin(t*1.8f)*8,Offset(107f,-93f)){outlined(shape(87f,-133f,206f,-180f,181f,-113f,218f,-64f,97f,-78f),skin)}
                    drawOval(Brush.verticalGradient(listOf(skin,Color(0xFF3D7393)),-208f,-20f),Offset(-171f,-204f),Size(315f,181f));drawOval(Color(0xFFD2E7DE),Offset(-142f,-89f),Size(221f,61f));drawCircle(ink,10f,Offset(-119f,-124f));drawCircle(Color.White,3f,Offset(-122f,-128f));drawArc(ink,15f,70f,false,Offset(-163f,-118f),Size(69f,49f),style=Stroke(3f));rotate(sin(t*2.2f)*13,Offset(-5f,-76f)){drawOval(Color(0xFF64B0C8),Offset(-12f,-78f),Size(108f,52f))};for(i in 0..2)drawCircle(p.light.copy(alpha=.5f-i*.12f),8f+i*5,Offset(-65f+i*33,-234f-i*22f+sin(t+i)*10))}
            }
            else->{
                for(side in listOf(-1,1))rotate(side*(sin(t*2.4f)*9+attack*19),Offset(side*44f,-166f)){outlined(shape(side*34f,-172f,side*179f,-259f,side*154f,-100f,side*99f,-142f,side*57f,-85f),Color(0xFF70548D));drawLine(skin,Offset(side*42f,-172f),Offset(side*162f,-231f),5f);drawLine(skin,Offset(side*42f,-172f),Offset(side*143f,-115f),4f)}
                drawPath(Path().apply{moveTo(51f,-48f);cubicTo(120f,-14f,174f,-68f,187f,-29f);cubicTo(180f,8f,92f,29f,24f,-10f);close()},Color(0xFF795B9D))
                drawOval(Brush.verticalGradient(listOf(skin,Color(0xFF49385F)),-213f,0f),Offset(-68f,-215f),Size(139f,209f));drawOval(Color(0xFFC5A5C8),Offset(-39f,-113f),Size(73f,96f))
                for(side in listOf(-1,1)){plate(skin,side*43f-20,-46f,41f,43f,15f);drawPath(shape(side*27f,-210f,side*57f,-284f,side*61f,-225f),Color(0xFFE6C48C))}
                drawOval(skin,Offset(-99f,-244f),Size(142f,104f));drawPath(shape(-109f,-201f,-47f,-195f,-59f,-151f,-121f,-163f),skin);drawCircle(eye,9f,Offset(-47f,-208f));drawPath(shape(-75f,-170f,-58f,-165f,-65f,-149f),Color(0xFFF9E9C6));drawPath(shape(-28f,-237f,-3f,-261f,19f,-236f),Color(0xFFE3BA78))
            }
        }
        if(hit>.05f){drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha=hit*.4f),Color.Transparent),Offset(-40f,-155f),135f),135f,Offset(-40f,-155f))}
    }
}
