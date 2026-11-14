package dev.pleos.entry.boss.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import dev.pleos.boss.game.*
import kotlin.math.*

internal enum class WeaponMotion(val title: String) { BLADE("새싹 연속 베기"), HAMMER("모래 분쇄"), SPEAR("별빛 돌진"), STAFF("밤빛 마법탄") }
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

/** A layered paper doll. Every visual item comes from the equipped inventory. */
internal fun DrawScope.drawAdventurer(look: HeroLook, time: Float, swing: Float=0f, guard: Float=0f, ultimate: Float=0f) {
    val accent=look.accent;val armor=look.armorColor;val breathe=sin(time*2.4f)*2f
    val dark=Color(0xFF1C302C);val skin=Color(0xFFE8BC93)
    if(look.aura>0 || ultimate>0f) {
        val a=.08f+look.aura*.035f+ultimate*.18f
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha=a),Color.Transparent),Offset(0f,-110f),160f),160f,Offset(0f,-110f))
        if(look.aura>=2)for(i in 0..7){val angle=time*.6f+i*PI.toFloat()/4;drawCircle(accent.copy(alpha=.5f),2f,Offset(cos(angle)*70f,-100f+sin(angle)*95f))}
    }
    // Cape and boots sit behind the torso; their movement follows the attack pose.
    val cape=Path().apply {moveTo(-24f,-177f+breathe);cubicTo(-60f,-151f,-53f,-95f,-87f-swing*24f,-45f);lineTo(-47f,-57f);lineTo(-20f,-35f);cubicTo(5f,-86f,20f,-140f,16f,-172f);close()}
    outlined(cape,if(look.armor?.templateId=="cloud_robe")Color(0xFF88BACB) else armor.copy(red=armor.red*.55f,green=armor.green*.65f,blue=armor.blue*.62f))
    drawPath(cape,Brush.horizontalGradient(listOf(Color.White.copy(alpha=.10f),Color.Transparent),-75f,0f))
    rotate(-10f-swing*17f,Offset(-18f,-76f)) { plate(armor,-36f,-87f,29f,60f);plate(dark,-40f,-34f,44f,30f,9f);drawLine(accent.copy(alpha=.5f),Offset(-34f,-13f),Offset(-6f,-13f),3f) }
    rotate(8f+swing*24f,Offset(20f,-79f)) { plate(armor,4f,-86f,31f,62f);plate(dark,3f,-32f,48f,29f,9f);drawLine(accent.copy(alpha=.5f),Offset(11f,-11f),Offset(43f,-11f),3f) }
    translate(guard*-8f,breathe) {
        // Back arm and shield.
        rotate(guard*-25f,Offset(-35f,-157f)) { plate(armor,-63f,-161f,29f,63f,14f);drawCircle(skin,12f,Offset(-48f,-102f)) }
        outlined(shape(-72f,-145f,-29f,-147f,-24f,-109f,-48f,-84f,-75f,-111f),armor)
        drawPath(shape(-65f,-138f,-36f,-139f,-33f,-113f,-48f,-96f,-66f,-113f),accent.copy(alpha=.25f))
        drawLine(Color(0xFFEDD69A),Offset(-49f,-137f),Offset(-49f,-106f),4f)
        // Armor changes both colour and silhouette, not just an icon.
        val robe=look.armor?.templateId=="cloud_robe"
        outlined(shape(-34f,-171f,28f,-174f,40f,-103f,if(robe)53f else 33f,if(robe)-47f else -78f,if(robe)-47f else -35f,if(robe)-47f else -78f,-41f,-108f),armor)
        drawPath(shape(-24f,-163f,12f,-164f,24f,-113f,-20f,-112f),Brush.verticalGradient(listOf(Color.White.copy(alpha=.18f),Color.Transparent),-164f,-100f))
        plate(Color(0xFF68533B),-40f,-99f,81f,16f,4f)
        drawRoundRect(Color(0xFFEBC784),Offset(-7f,-98f),Size(19f,16f),CornerRadius(3f,3f),style=Stroke(3f))
        if(look.armor?.templateId=="shell_mail")for(i in 0..2)drawArc(Color(0xFFEFCA89),0f,180f,false,Offset(-28f,-156f+i*18f),Size(53f,22f),style=Stroke(2f))
        if(look.armor?.templateId=="iron_plate"){outlined(shape(-52f,-172f,-28f,-185f,-10f,-165f,-24f,-147f,-51f,-148f),armor);outlined(shape(20f,-177f,50f,-176f,59f,-151f,29f,-148f),armor)}
        drawCircle(Color(0xFFFFD98B),7f,Offset(-4f,-143f));drawCircle(accent,3.5f,Offset(-4f,-144f))
        // Hair, face and scarf give the adventurer an identity even with starter gear.
        drawCircle(dark,43f,Offset(-1f,-206f))
        drawRoundRect(skin,Offset(-25f,-235f),Size(62f,57f),CornerRadius(22f,22f))
        outlined(shape(-40f,-218f,-32f,-247f,-6f,-261f,24f,-247f,47f,-223f,17f,-226f,12f,-242f,-1f,-224f,-18f,-230f,-20f,-208f),Color(0xFF27352C))
        drawLine(Color(0xFF815D39),Offset(-25f,-242f),Offset(-6f,-251f),5f,StrokeCap.Round)
        drawRoundRect(Color(0xFF23362F),Offset(15f,-213f),Size(6f,13f),CornerRadius(3f,3f))
        drawCircle(Color.White,1.6f,Offset(17f,-211f))
        drawLine(Color(0xFF98714F),Offset(24f,-192f),Offset(30f,-193f),2f,StrokeCap.Round)
        outlined(shape(-29f,-181f,24f,-181f,31f,-166f,-16f,-166f,-39f,-174f),accent)
        outlined(shape(-31f,-177f,-70f-sin(time*3)*8f,-185f,-63f-sin(time*3)*8f,-166f,-27f,-165f),accent.copy(alpha=.8f))
        if(look.level>=10){drawPath(shape(-20f,-245f,12f,-251f,22f,-239f,-15f,-231f),Color(0xFFE0B96C));drawCircle(accent,4f,Offset(4f,-242f))}
        // Front arm winds up, swings and recovers in a readable arc.
        val angle=when(look.motion){WeaponMotion.STAFF -> -14f-swing*20;WeaponMotion.SPEAR->-18f+swing*11;WeaponMotion.HAMMER->-85f+swing*143;else->-53f+swing*105}
        rotate(angle,Offset(32f,-152f)) {
            plate(armor,24f,-162f,28f,64f,13f)
            drawCircle(skin,13f,Offset(41f,-99f))
            val weaponAngle=when(look.motion){WeaponMotion.HAMMER->75f;WeaponMotion.SPEAR->75f-angle;WeaponMotion.STAFF->8f-angle;else->-24f}
            translate(44f,-105f){rotate(weaponAngle,Offset.Zero){drawWeapon(look.weapon?.templateId?:"twig_blade",accent,look.weapon?.upgradeLevel?:0,time)}}
            plate(armor,25f,-121f,29f,19f,6f)
        }
    }
    look.charm?.let { charm ->
        val cx=-88f+sin(time*1.3f)*9f;val cy=-218f+sin(time*2.1f)*9f
        val cc=when(charm.templateId){"moss_charm"->Color(0xFF9AE5B2);"whale_charm"->Color(0xFF93DFF9);"dragon_charm"->Color(0xFFD0A0FF);else->Color(0xFFFFDA8C)}
        drawCircle(Brush.radialGradient(listOf(cc.copy(alpha=.35f),Color.Transparent),Offset(cx,cy),38f),38f,Offset(cx,cy))
        translate(cx,cy) {
            if(charm.templateId=="lantern_charm"){drawRoundRect(cc,Offset(-12f,-18f),Size(24f,32f),CornerRadius(6f,6f));drawLine(dark,Offset(-10f,-9f),Offset(10f,-9f),3f);drawArc(cc,-180f,180f,false,Offset(-8f,-27f),Size(16f,15f),style=Stroke(3f))}
            else {drawOval(cc,Offset(-21f,-15f),Size(42f,32f));drawPath(shape(-14f,8f,-22f,22f,1f,13f),cc);drawCircle(dark,3f,Offset(8f,-3f));if(charm.templateId=="dragon_charm"){drawPath(shape(-8f,-9f,-16f,-29f,4f,-14f),cc);drawPath(shape(-14f,1f,-36f,-13f,-29f,13f),cc.copy(alpha=.65f))}}
        }
    }
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

@Composable internal fun HeroPortrait(game: GameState,modifier: Modifier=Modifier) {
    val look=heroLook(game)
    Canvas(modifier) {
        val longWeapon=look.motion==WeaponMotion.SPEAR
        val s=minOf(size.width/(if(longWeapon)380f else 260f),size.height/330f)
        val anchor=size.width*(if(longWeapon).36f else .53f)
        withTransform({translate(anchor,size.height*.93f);scale(s,s,Offset.Zero)}){
            drawOval(Color.Black.copy(alpha=.35f),Offset(-70f,-4f),Size(150f,18f));drawAdventurer(look,0f)
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
