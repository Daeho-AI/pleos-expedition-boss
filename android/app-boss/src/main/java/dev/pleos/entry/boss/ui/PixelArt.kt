package dev.pleos.entry.boss.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.sin

/** Small, deliberately authored pixel silhouettes. Each palette layer follows real equipment. */
private val knight = listOf(
    "..........oooooo........",
    "........oocccchhoo......",
    ".......ochhhhhhhcco.....",
    "......ochhaaaaahhhco....",
    ".....ochhaaaaaaahhcco...",
    ".....ohhaaasssaaahhco...",
    ".....ohhaoodddooahhco...",
    ".....ohaaodccdooaahco...",
    "......oaaoddddoaacco....",
    "......ooaahhhhaacco.....",
    ".......ooaaagaaaoo......",
    ".....ookoogggoookoo.....",
    "....okkkoddgddokkkko....",
    "...okkkoshhhhaookkkko...",
    "...okkoahaassaahoakko...",
    "..okkoahhaassaaahhako...",
    "..okkoahhoaaaooahhako...",
    "..okkoassoaaaoosssako...",
    "..okkoassoaaaoosssako...",
    "..okkooaooggggoaoooko...",
    ".okkkoodoghhgodoodkko...",
    ".okkkoodoogggoododkko...",
    ".okkkooaaooooaaoookko...",
    ".okkkooaaoddoaaoookko...",
    "..okkooaaoddoaaoooko....",
    "...okooaaoddoaaooo......",
    "....ooassoddoasso.......",
    ".....ossso..ossso.......",
    ".....odddo..odddo.......",
    "....oodddo..odddoo......",
    "....ohhddo..oddhho......",
    "....oooooo..oooooo......",
)
private val blade = listOf("....h...","...hho..","..hhco..","..hhco..","..hhco..","..hhco..","..hhco..","..hhco..","..hhco..","..hhco..","..hhco..","..hhco..",".oggggo.","ooggggoo","...go...","...do...","...do...","..oggo..")
private val hammer = listOf(".ooooooo.","ohhhhhhao","ohhaaacco","oaaaaccco",".ooogooo.","....go...","....go...","....go...","....go...","....go...","....do...","....do...","....do...","....go...","....oo...")
private val spear = listOf("....h....","...hho...","..hhhco..",".ohhhcco.","..ohhco..","...oco...","..ggggg..","....go...","....go...","....go...","....go...","....go...","....go...","....go...","....go...","....go...","....go...","....go...","....go...","....go...","....oo...")
private val staff = listOf("...hhh...","..hccch..",".hcccccgo",".hcchccgo","..hccch..","...ggg...","....go...","....go...","....go...","....go...","....go...","....go...","....go...","....go...","....go...","....do...","....oo...")
private val charm = listOf("...cc...","..chhc..",".chhhhc.",".chhhhc.","..chhc..","...cc...")

private val guardian = listOf(
    ".....gg........gg.......","....gggg..gg..gggg......","...ooggggggggggggoo.....","..ossgggssgggsssssso....","..ossssshssssshsssso....","...osssoossssoossso.....","...ossoccosoccosso......","....osssoooosssso.......","....ooosggggsooo........","..ooossssssssssooo......",".osssshhsssshhhssso.....","osssshhssssssshhssso....","osssssooshhsoossssso....","osssso..shhs..osssso....",".osso..osssso..osso.....","..oo..osssssso..oo......",".....osssosssso.........","....ossssoosssso........","....ohhssoosshho........","....ooooo..ooooo........")
private val turtle = listOf(
    ".........oooooo.........",".......oohhhhhhoo.......",".....oohhassssaahho.....","....ohhasaaaasaaahho....","...ohassahhhhasasshho...","...oassaahssaahaassso...","..ohassaahssaahaasssho..","..ohassahhhhhaassssho..","..oaaasssssssssssaao...","..oaassaahhhhaasssao...","...oaahhassssahhaao....","....oaaassssssaaao.....","..oo.ooooaaaaoooo.oo...",".ohhooo.oooooo.ooohho..","ohhssaaaassssaaaasshho.","ohsoosaassssssaasoosho.",".osooshhsssssshhsooso..","..oohhhooooooohhhoo....","...oooo........oooo....")
private val owl = listOf(
    "...oo..............oo...","...oho............oho...","...ohho..........ohho...","....ohhoo......oohho....","....ohhaaaooooaaahho....","...ohhaasssssssaahhho...","..ohhaahhhhsshhhhaahho..",".ohhaahgggghhgggghaahho.","ohhaahghohghhghohghaahho","ohhaahgggghhhgggghaahho.","ohhssaahhhhoohhhhaasshho",".ohhssaahhoggohaaasshho.","..ohhssaaahggaaasshhho..","...ohhssaaaaaaaasshho...","....ohhssaaaaaasshho....",".....ohhssaaaasshho.....","......ohhhssshhho.......",".......ohhhhhho.........",".......oggooggo.........","......oggggggggo........")
private val boar = listOf(
    ".....oo..........oo.....","....ohho........ohho....","....ohaaooooooooaaho....","....oaashhhhhhssaao.....","...oaassaaahaaasssao....","..ohasssaaahaaasssaho...","..ohasssooshsoosssaho...",".ohhasssocchccosssahho..","ohhhassssoooossssaahhho.","ohhhaassahhhhasaahhhhho.",".ohhaasahgggghasaahhho..","..ohhhaaaggggaaahhho...","..ohhsssahhhhas sshho...","...ohhsssssssssshho.....","....ohhssaaaasshho......",".....ohhsssssshho.......","....ohhsooooosshho......","....ohhso...osshho......","....oooo.....oooo.......")
private val whale = listOf(
    ".........hh.............","........hch.............",".........c..............",".......ooooooo..........",".....oohhhhhhhoo........","....ohhhhaaaaahho.......","...ohhaaaaaaaaahho......","..ohhaaaaaaaaasssho.....",".ohhaaaaaaaaasssssho....","ohhhaahoaaaaassssssho...","ohhaaaaoaaaaassssssho...","ohhaaaaaaaahhhsssssso...","ohhhaaaaahhhhsssssho....",".ohhhhahhhhhsssssso.....","..ohhhhhhhssssssso..oo..","...oohhhhssssssso.oho...",".....ooosssssoooohho....",".......ooooooooahho.....",".............oooo.......")
private val dragon = listOf(
    "....hh..............hh..","....hho............ohh..","....hcho..........ohch..",".....hchho......ohhch...","......hchhaoooaaahch....",".....oahhassssssaahho...","..oooaahsasssssshahhaooo",".ohhhasssossssoassshhhho","ohhasssssccssccssssssaho","ohhasssssoooooosssssssho",".ohhassssahhhasssssshho.","..ohhassahggghasssshho..","...ohhaasaggasaasshho...","....ohhaaaaaaaaahhho....","...ohhhoossssoohhhho....","..ohhhooossssoooohhho...",".ohhho..osssso..ohhhho..","..ooo..ossssssso..ooo...","......ossssosssso.......",".....ohhhsoosshhho......",".....ooooo..oooooo......")
private val monsters = listOf(guardian,turtle,owl,boar,whale,dragon)

private fun DrawScope.sprite(rows:List<String>,palette:Map<Char,Color>,unit:Float,x:Float,y:Float){
    rows.forEachIndexed{row,line->line.forEachIndexed{col,c->palette[c]?.let{drawRect(it,Offset(x+col*unit,y+row*unit),Size(unit,unit))}}}
}
private fun palette(armor:Color,accent:Color)=mapOf('o' to Color(0xFF0A1121),'d' to Color(0xFF243048),'s' to Color(0xFF3B5067),'a' to armor,'h' to Color(0xFFD9EAF2),'c' to accent,'g' to Color(0xFFD7AC70),'k' to Color(0xFF244859))

internal fun DrawScope.pixelWeapon(id:String,tint:Color,unit:Float,x:Float,y:Float){
    val rows=when(id){"sand_hammer"->hammer;"star_spear"->spear;"night_staff"->staff;else->blade}
    sprite(rows,palette(Color(0xFF8B9BB5),tint),unit,x,y)
}
internal fun DrawScope.pixelHero(look:HeroLook,time:Float,unit:Float,charged:Float=0f){
    val bob=if(sin(time*2.4f)>.4f)-unit else 0f
    val p=palette(look.armorColor,look.accent)
    translate(0f,bob){
        sprite(knight,p,unit,-12*unit,-32*unit)
        val weaponY=if(look.motion==WeaponMotion.SPEAR)-32*unit else -26*unit
        pixelWeapon(look.weapon?.templateId?:"twig_blade",look.accent,unit,9*unit,weaponY)
        if(look.charm!=null)sprite(charm,mapOf('c' to rarityInk(look.charm.rarity),'h' to Color(0xFFEAF5FF)),unit,-21*unit,-25*unit+(if(sin(time*2)>.4f)-unit else unit))
        if((look.weapon?.upgradeLevel?:0)>=3||charged>0f){
            val a=.45f+charged*.4f
            for(i in 0..3){val py=-8*unit-((time*10+i*7)%25).toInt()*unit;drawRect(look.accent.copy(alpha=a),Offset((15+i%2*3)*unit,py),Size(unit,unit))}
        }
        if((look.weapon?.upgradeLevel?:0)>=5)for(i in 0..4)drawRect(look.accent.copy(alpha=.6f),Offset((-19+i*8)*unit,-((time*8+i*6)%30).toInt()*unit),Size(unit,unit))
    }
}

internal fun DrawScope.pixelMonster(index:Int,time:Float,unit:Float,hit:Float=0f){
    val i=index.mod(monsters.size);val rows=monsters[i]
    val armor=listOf(0xFF76A893,0xFFC4A776,0xFFA8A0CF,0xFF879CB3,0xFF80BACB,0xFF9B93CC)[i]
    val p=palette(Color(armor),worlds[i].light).toMutableMap()
    if(i==0){p['g']=Color(0xFF90B78A);p['s']=Color(0xFF547864)}
    if(hit>.2f){p.keys.filter{it!='o'}.forEach{key->p[key]=androidx.compose.ui.graphics.lerp(p[key]!!,Color(0xFFFFEDBF),hit*.65f)}}
    val bob=if(sin(time*2f+i)>.4f)-unit else 0f
    val shake=if(hit>.4f)(if((hit*12).toInt()%2==0)unit else -unit) else 0f
    sprite(rows,p,unit,-rows.maxOf{it.length}*unit/2+shake,-rows.size*unit+bob)
}
