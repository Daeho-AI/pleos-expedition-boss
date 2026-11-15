package dev.pleos.entry.boss.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import dev.pleos.boss.game.*
import kotlin.math.*

internal enum class WeaponMotion(val title: String) { BLADE("새싹 에너지파"), HAMMER("모래 파동"), SPEAR("별빛 관통탄"), STAFF("밤빛 마법탄") }
internal data class HeroLook(val weapon: Equipment?, val armor: Equipment?, val charm: Equipment?, val level: Int) {
    val motion get() = when(weapon?.templateId) { "sand_hammer" -> WeaponMotion.HAMMER; "star_spear" -> WeaponMotion.SPEAR; "night_staff" -> WeaponMotion.STAFF; else -> WeaponMotion.BLADE }
    val accent get() = when(motion) { WeaponMotion.BLADE -> Color(0xFF88F4C0); WeaponMotion.HAMMER -> Color(0xFFFFC879); WeaponMotion.SPEAR -> Color(0xFF9AE9FF); WeaponMotion.STAFF -> Color(0xFFC2AAFF) }
    val armorColor get() = when(armor?.templateId) { "leaf_coat" -> Color(0xFF5AAB81); "shell_mail" -> Color(0xFFC89C66); "cloud_robe" -> Color(0xFFB5D9EC); "iron_plate" -> Color(0xFF9BA9BB); else -> Color(0xFF71978A) }
    val aura get() = maxOf(weapon?.rarity?.ordinal?:0, armor?.rarity?.ordinal?:0, charm?.rarity?.ordinal?:0)
}
internal fun heroLook(game: GameState) = HeroLook(game.equipment.find{it.key==game.equipped[Slot.WEAPON]},game.equipment.find{it.key==game.equipped[Slot.ARMOR]},game.equipment.find{it.key==game.equipped[Slot.CHARM]},game.hero.level)
internal fun rarityInk(r: Rarity?) = when(r) { Rarity.RARE -> Color(0xFF80D6F2); Rarity.EPIC -> Color(0xFFC3A1FF); Rarity.LEGENDARY -> Color(0xFFFFCC73); else -> Color(0xFFC0CFC8) }
internal fun shape(vararg xy: Float) = Path().apply { moveTo(xy[0],xy[1]); for(i in 2 until xy.size step 2)lineTo(xy[i],xy[i+1]);close() }
internal fun DrawScope.outlined(path: Path, color: Color, width: Float=3f) { drawPath(path,color);drawPath(path,Color(0xFF132720),style=Stroke(width,join=StrokeJoin.Round)) }
internal fun DrawScope.plate(color: Color, x: Float,y: Float,w: Float,h: Float,r: Float=8f) {
    drawRoundRect(Color(0xFF10221E),Offset(x-2,y-2),Size(w+4,h+4),CornerRadius(r+2,r+2))
    drawRoundRect(Brush.linearGradient(listOf(color,Color(0xFF243C38)),Offset(x,y),Offset(x+w,y+h)),Offset(x,y),Size(w,h),CornerRadius(r,r))
    drawLine(color.copy(alpha=.7f),Offset(x+6,y+4),Offset(x+w-6,y+4),2f)
}

/** Pixel layers are derived from the equipped inventory, including armor and charm. */
internal fun DrawScope.drawAdventurer(look:HeroLook,time:Float,swing:Float=0f,guard:Float=0f,ultimate:Float=0f){
    pixelHero(look,time,7f,ultimate)
}

internal fun DrawScope.drawWeapon(id: String, tint: Color, upgrade: Int=0, time: Float=0f) {
    val metal=Color(0xFFE3E9D5);val edge=Color(0xFF35534A)
    if(upgrade>=3)drawCircle(Brush.radialGradient(listOf(tint.copy(alpha=.18f+upgrade*.015f),Color.Transparent),Offset(8f,-64f),95f),95f,Offset(8f,-64f))
    drawLine(Color(0xFF483A2C),Offset(0f,24f),Offset(0f,-65f),11f,StrokeCap.Round)
    drawLine(Color(0xFFAF9060),Offset(-2f,22f),Offset(-2f,-65f),4f,StrokeCap.Round)
    when(id){
        "sand_hammer"->{plate(Color(0xFFB49460),-36f,-111f,74f,53f,7f);drawPath(shape(-31f,-104f,28f,-104f,28f,-96f,-31f,-96f),metal);drawCircle(tint,10f,Offset(0f,-85f));drawPath(shape(-43f,-105f,-32f,-115f,-32f,-59f,-43f,-68f),edge)}
        "star_spear"->{drawLine(Color(0xFFC2BD94),Offset(0f,66f),Offset(0f,-133f),7f);outlined(shape(0f,-182f,18f,-139f,0f,-114f,-18f,-139f),metal);drawPath(shape(0f,-178f,0f,-120f,13f,-139f),tint);drawLine(tint,Offset(-22f,-117f),Offset(22f,-117f),5f)}
        "night_staff"->{drawLine(Color(0xFF685B85),Offset(0f,43f),Offset(0f,-104f),12f,StrokeCap.Round);drawArc(Color(0xFFDAC9A5),30f,285f,false,Offset(-29f,-154f),Size(58f,65f),style=Stroke(9f,cap=StrokeCap.Round));drawCircle(Brush.radialGradient(listOf(Color.White,tint,tint.copy(alpha=.05f)),Offset(0f,-122f),23f),23f,Offset(0f,-122f));drawCircle(tint,3f,Offset(cos(time*3)*35f,-122f+sin(time*3)*35f))}
        else->{outlined(shape(-9f,-24f,-17f,-115f,1f,-153f,18f,-115f,10f,-24f),metal);drawPath(shape(1f,-147f,13f,-113f,7f,-30f,1f,-30f),tint);drawLine(Color(0xFFDFC18B),Offset(-24f,-25f),Offset(24f,-25f),9f,StrokeCap.Round);drawCircle(tint,5f,Offset(0f,-25f));drawCircle(Color(0xFFE1BB73),7f,Offset(0f,25f))}
    }
    if(upgrade>=5)for(i in 0..3){val yy=-20f-((time*28+i*30)%130);drawCircle(tint.copy(alpha=.7f),2f,Offset(sin(time*3+i)*22,yy))}
}

@Composable internal fun HeroPortrait(game:GameState,modifier:Modifier=Modifier){
    val look=heroLook(game)
    Canvas(modifier){
        val unit=maxOf(2f,floor(minOf(size.width/46f,size.height/38f)))
        translate(size.width*.48f,size.height*.93f){
            drawRect(Color(0xFF0A1321),Offset(-14*unit,unit),Size(30*unit,2*unit))
            pixelHero(look,0f,unit)
        }
    }
}
@Composable internal fun EquipmentArt(item: Equipment, slot: Slot, modifier: Modifier=Modifier) {
    Canvas(modifier){val color=rarityInk(item.rarity);val s=minOf(size.width,size.height)/190f
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha=.2f),Color.Transparent)),size.minDimension*.48f)
        withTransform({translate(size.width*.5f,size.height*.66f);scale(s,s,Offset.Zero)}){
            when(slot){
                Slot.WEAPON->rotate(28f,Offset.Zero){drawWeapon(item.templateId,color,item.upgradeLevel)}
                Slot.ARMOR->{val armor=HeroLook(null,item,null,1).armorColor;outlined(shape(-35f,-102f,-70f,-78f,-56f,-34f,-34f,-48f,-41f,20f,40f,20f,34f,-49f,55f,-34f,70f,-80f,34f,-102f,18f,-86f,-18f,-86f),armor);plate(Color(0xFF65533C),-37f,-10f,74f,14f,3f);drawCircle(color,10f,Offset(0f,-59f));drawPath(shape(-20f,-79f,0f,-64f,21f,-79f,14f,-27f,-15f,-27f),color.copy(alpha=.25f))}
                Slot.CHARM->{drawArc(Color(0xFFCEB47A),-180f,325f,false,Offset(-35f,-111f),Size(70f,78f),style=Stroke(5f));outlined(shape(0f,-52f,31f,-17f,0f,20f,-31f,-17f),color);drawPath(shape(0f,-43f,0f,11f,23f,-17f),Color.White.copy(alpha=.3f));drawCircle(Color.White.copy(alpha=.8f),5f,Offset(-7f,-23f))}
            }
        }
    }
}
