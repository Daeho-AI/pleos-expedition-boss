package dev.pleos.entry.boss.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import dev.pleos.boss.game.GameState
import kotlin.math.sin

/** Quiet camp view. The adventurer uses the player's actual equipped items. */
@Composable internal fun LobbyScene(game:GameState,index:Int,modifier:Modifier=Modifier){
    val motion=rememberInfiniteTransition(label="camp")
    val time by motion.animateFloat(0f,20f,infiniteRepeatable(tween(20_000,easing=LinearEasing)),label="breeze")
    val dark=Panel.luminance()<.5f
    val look=heroLook(game)
    Canvas(modifier){
        val sx=size.width/1200f;val sy=size.height/440f
        withTransform({scale(sx,sy,Offset.Zero)}){
            val sky=if(dark)Color(0xFF242F3A) else Color(0xFFEDF2EE)
            val distant=if(dark)Color(0xFF34434A) else Color(0xFFD4DFD6)
            val ground=if(dark)Color(0xFF3A4A48) else Color(0xFFC2D1B9)
            val grass=if(dark)Color(0xFF42574F) else Color(0xFFA7BEA1)
            val stone=if(dark)Color(0xFF65736F) else Color(0xFFA0B3A6)
            drawRect(sky,Offset.Zero,Size(1200f,440f))
            drawCircle(if(dark)Color(0xFFC4D1D1) else Color(0xFFFFFCED),36f,Offset(650f,65f))
            drawPath(shape(0f,230f,0f,180f,160f,180f,160f,148f,240f,148f,240f,179f,360f,179f,360f,131f,405f,131f,405f,116f,447f,116f,447f,171f,570f,171f,570f,145f,705f,145f,705f,185f,902f,185f,902f,143f,1030f,143f,1030f,180f,1200f,180f,1200f,270f),distant)
            drawPath(shape(0f,250f,115f,240f,115f,222f,300f,222f,300f,240f,550f,240f,550f,212f,740f,212f,740f,230f,1020f,230f,1020f,208f,1200f,208f,1200f,440f,0f,440f),ground)
            drawPath(shape(0f,330f,210f,330f,210f,316f,510f,316f,510f,333f,790f,333f,790f,313f,1015f,313f,1015f,295f,1200f,295f,1200f,440f,0f,440f),grass)
            // Stepping stones lead from the camp to the next boss gate.
            for(i in 0..6){val x=480f+i*52;val y=357f-i*7
                drawRect(stone,Offset(x,y),Size(38f,10f))
                drawRect(sky.copy(alpha=.45f),Offset(x+2,y),Size(32f,3f))
            }
            fun tree(x:Float,y:Float,s:Float){
                translate(x,y){scale(s,s,Offset.Zero){
                    drawRect(stone,Offset(-8f,-95f),Size(16f,100f))
                    drawPath(shape(-70f,-90f,-70f,-135f,-52f,-135f,-52f,-175f,-28f,-175f,-28f,-210f,30f,-210f,30f,-178f,57f,-178f,57f,-145f,77f,-145f,77f,-93f),if(dark)Color(0xFF3C5D55) else Color(0xFF8FAE99))
                    drawRect(if(dark)Color(0xFF4D6D5F) else Color(0xFFAAC0A4),Offset(-52f,-135f),Size(105f,42f))
                }}
            }
            tree(95f,350f,1.1f);tree(1110f,350f,1.3f);tree(1000f,285f,.7f)
            // Ruined gateway, deliberately smaller than the equipped hero.
            drawRect(stone,Offset(804f,187f),Size(22f,126f))
            drawRect(stone,Offset(900f,187f),Size(22f,126f))
            drawRect(stone,Offset(815f,165f),Size(96f,28f))
            drawRect(stone.copy(alpha=.45f),Offset(824f,193f),Size(77f,120f))
            drawRect(if(dark)Color(0xFF293C38) else Color(0xFF7D968B),Offset(831f,200f),Size(62f,113f))
            drawRect(sky.copy(alpha=.4f),Offset(815f,165f),Size(96f,5f))
            translate(863f,311f){scale(1f,sx/sy,Offset.Zero){pixelMonster(index,time,2.5f)}}
            // Raised campsite with a flag and a closed supply chest.
            drawPath(shape(223f,377f,410f,377f,445f,393f,408f,411f,235f,411f,197f,393f),stone)
            drawPath(shape(223f,372f,410f,372f,445f,388f,408f,402f,235f,402f,197f,388f),ground)
            drawRect(stone,Offset(188f,192f),Size(7f,196f))
            val flutter=if(sin(time*1.5f)>0)5f else 0f
            drawPath(shape(195f,202f,257f,202f+flutter,247f,235f+flutter,257f,259f+flutter,195f,254f),if(dark)Color(0xFF7794A6) else Color(0xFF7B96AA))
            drawRect(Color(0xFF8C795F),Offset(447f,365f),Size(37f,27f))
            drawRect(Color(0xFFC8B28C),Offset(447f,365f),Size(37f,6f))
            drawRect(Color(0xFFE4CF9F),Offset(462f,374f),Size(7f,9f))
            translate(320f,387f){scale(1f,sx/sy,Offset.Zero){
                drawOval(Color.Black.copy(alpha=.10f),Offset(-70f,-5f),Size(155f,14f))
                pixelHero(look,time,5.5f)
            }}
            for(i in 0..27){val x=(i*137%1180+10).toFloat();val y=(i*29%92+340).toFloat()
                if(x !in 180f..930f){drawRect(stone.copy(alpha=.5f),Offset(x,y),Size(3f,7f));if(i%3==0)drawRect(sky,Offset(x-2,y-3),Size(7f,5f))}
            }
        }
    }
}
