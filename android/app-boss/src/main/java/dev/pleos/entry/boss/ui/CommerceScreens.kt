package dev.pleos.entry.boss.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pleos.boss.game.*
import dev.pleos.entry.boss.BossGraph
import dev.pleos.entry.boss.social.*
import kotlinx.coroutines.delay

private data class Confirmation(val title: String, val description: String, val action: () -> Unit)
@Composable private fun ConfirmBox(value: Confirmation?, dismiss: () -> Unit) {
    if (value != null) AlertDialog(onDismissRequest=dismiss, title={Text(value.title)}, text={Text(value.description)},
        confirmButton={TextButton(onClick={dismiss();value.action()},Modifier.heightIn(min=64.dp).testTag("confirm-action")){Text("확인")}},
        dismissButton={TextButton(onClick=dismiss,modifier=Modifier.heightIn(min=64.dp)){Text("취소")}})
}
private fun itemLabel(item: Equipment, balance: Balance): String {
    val rarity = when(item.rarity){Rarity.COMMON->"일반";Rarity.RARE->"희귀";Rarity.EPIC->"영웅";Rarity.LEGENDARY->"전설"}
    return (balance.equipment.find{it.id==item.templateId}?.name ?: "장비") + " · $rarity +${item.upgradeLevel}"
}
@Composable private fun PageHeading(title: String, subtitle: String) {
    Text(title,fontSize=30.sp,fontWeight=FontWeight.Bold)
    Spacer(Modifier.height(8.dp));Text(subtitle,color=Muted);Spacer(Modifier.height(18.dp))
}
@Composable private fun Switches(values: List<String>, selected: String, choose: (String)->Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        values.forEach { value -> FilterChip(selected=selected==value,onClick={choose(value)},label={Text(value)},modifier=Modifier.heightIn(min=56.dp)) }
    }
    Spacer(Modifier.height(16.dp))
}
@Composable internal fun ShopScreen(game: GameState, graph: BossGraph, busy: Boolean) {
    var tab by rememberSaveable { mutableStateOf("재료") }
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }
    Column(Modifier.fillMaxSize().testTag("screen-shop")) {
        PageHeading("원정 상점", "모은 코인으로 필요한 장비와 조각을 직접 고르세요.")
        Switches(listOf("재료","장비","조각","판매","기록"),tab){tab=it}
        LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.weight(1f)) {
            if (tab == "판매") {
                if (game.equipment.isEmpty()) item { Text("판매할 장비가 없어요. 원정 보상을 먼저 열어 보세요.",color=Muted) }
                items(game.equipment,key={it.key}) { item ->
                    val worn = item.key in game.equipped.values
                    CardBox(Modifier.fillMaxWidth()) {
                        Text(itemLabel(item,graph.balance),fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                        Text(if(worn)"장착을 해제하면 판매할 수 있어요" else "판매 대금 · ${graph.economy.salePrice(item)} 코인",color=Muted)
                        Spacer(Modifier.height(10.dp))
                        ActionButton(if(worn)"장착 해제" else "판매하기",{
                            if(worn)graph.unequip(item.key) else confirmation=Confirmation("장비 판매", "${itemLabel(item,graph.balance)}을 판매하고 ${graph.economy.salePrice(item)} 코인을 받습니다."){graph.sell(item.key)}
                        },enabled=!busy)
                    }
                }
            } else if (tab == "기록") {
                if(game.economy.history.isEmpty())item{Text("상점 거래 내역이 여기에 남아요.",color=Muted)}
                items(game.economy.history.asReversed()) { event -> CardBox(Modifier.fillMaxWidth()) {
                    Text((if(event.kind=="BUY")"구매 · " else "판매 · ")+event.label,fontSize=20.sp)
                    Text("${if(event.coins>0)"+" else ""}${event.coins} 코인",color=Gold)
                } }
            } else {
                val products = graph.economy.catalog.filter { when(tab){"장비"->it.equipment!=null;"조각"->it.bossId!=null;else->it.dust>0} }
                items(products,key={it.id}) { product ->
                    val owned = product.equipment?.let { e -> game.equipment.any{it.key==e.key} || game.economy.deposits.any{it.item.key==e.key} } == true
                    val complete = product.bossId?.let { (game.collection[it]?:0)>=graph.balance.chest.collectionUnlockFragments } == true
                    val levelOk = game.hero.level>=product.level
                    CardBox(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(product.name,fontSize=22.sp,fontWeight=FontWeight.SemiBold)
                                Text(product.description+(if(product.level>1)" · Lv.${product.level}" else ""),color=Muted)
                            }
                            ActionButton(when{owned->"보유 중";complete->"해금 완료";!levelOk->"Lv.${product.level} 필요";else->"${product.price} 코인"},
                                {confirmation=Confirmation(product.name+" 구매", "${product.price} 코인을 사용합니다. "+product.description){graph.buy(product.id)}},
                                Modifier.testTag("buy-"+product.id),!busy&&!owned&&!complete&&levelOk&&game.hero.coins>=product.price)
                        }
                    }
                }
            }
        }
    }
    ConfirmBox(confirmation){confirmation=null}
}

@Composable internal fun FriendsScreen(game: GameState, graph: BossGraph) {
    val controller = graph.social
    val state by controller.state.collectAsStateWithLifecycle()
    val busy by controller.busy.collectAsStateWithLifecycle()
    val localBusy by graph.actionBusy.collectAsStateWithLifecycle()
    var server by rememberSaveable { mutableStateOf(state.server) }
    var nickname by rememberSaveable { mutableStateOf(state.nickname) }
    var friendCode by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf("친구") }
    var selectedFriend by rememberSaveable { mutableStateOf("") }
    var giveId by rememberSaveable { mutableStateOf("") }
    var wantId by rememberSaveable { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(controller) { while(true){controller.refresh();delay(10000)} }
    val snapshot = state.snapshot
    Column(Modifier.fillMaxSize().testTag("screen-friends")) {
        PageHeading("함께 채우는 원정", "친구와 서로 필요한 장비를 교환하세요.")
        if(state.error!=null) {
            Text(state.error!!,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("social-error"))
            Spacer(Modifier.height(10.dp))
        }
        if(state.profile == null) {
            CardBox(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text("친구 프로필 만들기",fontSize=22.sp,fontWeight=FontWeight.Bold)
                Text("닉네임과 친구 코드로 연결합니다. 서로 수락한 친구에게 교환함의 장비가 공개됩니다.",color=Muted)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(nickname,{nickname=it.take(20)},label={Text("닉네임")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("social-nickname"))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(server,{server=it},label={Text("친구와 함께 사용할 서버 주소")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("social-server"))
                Spacer(Modifier.height(16.dp))
                ActionButton(if(busy)"연결하는 중…" else "친구 연결 시작",{controller.connect(server,nickname)},Modifier.testTag("social-connect"),!busy&&nickname.isNotBlank()&&server.isNotBlank())
            }
        } else {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.profile!!.nickname+" · 친구 코드 "+state.profile!!.code,fontSize=22.sp,fontWeight=FontWeight.SemiBold)
                    Text("같은 서버를 사용하는 친구에게 코드를 알려 주세요.",color=Muted)
                }
                TextButton({clipboard.setText(AnnotatedString(state.profile!!.code))},modifier=Modifier.heightIn(min=64.dp)){Text("코드 복사")}
                ActionButton(if(busy)"연결 중…" else "새로고침",controller::refresh,enabled=!busy)
            }
            Switches(listOf("친구","교환함","교환","연결 정보"),tab){tab=it}
            LazyColumn(verticalArrangement=Arrangement.spacedBy(14.dp),modifier=Modifier.weight(1f)) {
                if(tab=="연결 정보") item {
                    CardBox(Modifier.fillMaxWidth()) { Text("연결된 서버",color=Muted);Text(state.server)
                        Spacer(Modifier.height(12.dp));Text("친구와 교환함은 이 서버에 저장됩니다. 서버가 꺼져 있어도 원정과 상점은 이용할 수 있어요.") }
                }
                if(tab=="친구") {
                    item { CardBox(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            OutlinedTextField(friendCode,{friendCode=it.uppercase().filter{c->c.isLetterOrDigit()}.take(8)},label={Text("친구 코드 8자리")},singleLine=true,modifier=Modifier.weight(1f).testTag("friend-code"))
                            ActionButton("친구 요청",{controller.sendFriend(friendCode)},Modifier.testTag("friend-send"),!busy&&friendCode.length==8)
                        }
                    } }
                    items(snapshot?.requests?:emptyList(),key={it.id}) { req -> CardBox(Modifier.fillMaxWidth()) {
                        Text(req.person.nickname+if(req.incoming)" 님의 친구 요청" else " 님에게 요청을 보냈어요",fontSize=20.sp)
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            if(req.incoming) {
                                ActionButton("수락",{controller.friendRequest(req.id,"accept")},enabled=!busy)
                                TextButton({controller.friendRequest(req.id,"decline")},enabled=!busy,modifier=Modifier.heightIn(min=64.dp)){Text("거절")}
                            } else TextButton({controller.friendRequest(req.id,"cancel")},enabled=!busy,modifier=Modifier.heightIn(min=64.dp)){Text("요청 취소")}
                        }
                    } }
                    if(snapshot?.friends.isNullOrEmpty())item{Text("아직 연결된 친구가 없어요. 친구 코드를 주고받아 보세요.",color=Muted)}
                    items(snapshot?.friends?:emptyList(),key={it.id}) { friend -> CardBox(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)){Text(friend.nickname,fontSize=22.sp);Text("교환함 장비 ${friend.items.size}개",color=Muted)}
                            ActionButton("교환 제안",{selectedFriend=friend.id;giveId="";wantId="";tab="교환"},enabled=!busy)
                            TextButton({confirmation=Confirmation("친구 삭제","대기 중인 두 사람의 교환도 취소됩니다."){controller.removeFriend(friend.id)}},enabled=!busy,modifier=Modifier.heightIn(min=64.dp)){Text("삭제")}
                        }
                    } }
                }
                if(tab=="교환함") {
                    item { Text("교환함에 맡기면 가방에서 빠지고, 친구에게 공개됩니다. 교환 중인 장비는 취소 후 받을 수 있어요.",color=Muted) }
                    items(game.economy.deposits,key={it.id}) { pending -> CardBox(Modifier.fillMaxWidth()) {
                        Text(itemLabel(pending.item,graph.balance));Text("전송 확인 대기 · 장비는 보관 중",color=Gold)
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            ActionButton("전송 다시 확인",controller::refresh,enabled=!busy)
                            TextButton({controller.abortDeposit(pending.id)},enabled=!busy,modifier=Modifier.heightIn(min=64.dp)){Text("전송 취소 확인")}
                        }
                    } }
                    item { Text("교환함의 장비",fontSize=22.sp,fontWeight=FontWeight.Bold) }
                    if(snapshot?.inventory.isNullOrEmpty())item{Text("아래 가방에서 교환할 장비를 맡겨 주세요.",color=Muted)}
                    items(snapshot?.inventory?:emptyList(),key={it.id}) { held -> CardBox(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(itemLabel(held.item,graph.balance),Modifier.weight(1f),fontSize=20.sp)
                            ActionButton(if(held.locked)"교환 대기 중" else "가방으로 받기",{
                                confirmation=Confirmation("가방으로 받기", "${itemLabel(held.item,graph.balance)}을 받습니다. 같은 장비가 있으면 높은 강화 수치를 유지하고 중복분은 가루로 바뀝니다."){controller.withdraw(held.id)}
                            },enabled=!busy&&!held.locked)
                        }
                    } }
                    item { Text("내 가방",fontSize=22.sp,fontWeight=FontWeight.Bold) }
                    items(game.equipment,key={"bag:"+it.key}) { item -> CardBox(Modifier.fillMaxWidth()) {
                        val worn = item.key in game.equipped.values
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(itemLabel(item,graph.balance),Modifier.weight(1f),fontSize=20.sp)
                            ActionButton(if(worn)"장착 해제" else "교환함에 맡기기",{
                                if(worn)graph.unequip(item.key) else confirmation=Confirmation("교환함에 맡기기", "${itemLabel(item,graph.balance)}을 친구에게 공개합니다. 교환함에 있는 동안 장착·강화·판매할 수 없습니다."){controller.deposit(item.key)}
                            },Modifier.testTag("deposit-"+item.key),!busy&&!localBusy)
                        }
                    } }
                }
                if(tab=="교환") {
                    val friend = snapshot?.friends?.find{it.id==selectedFriend}
                    val give = snapshot?.inventory?.find{it.id==giveId&&!it.locked}
                    val want = friend?.items?.find{it.id==wantId&&!it.locked}
                    item { Text("새 교환 제안",fontSize=22.sp,fontWeight=FontWeight.Bold) }
                    item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        snapshot?.friends?.forEach { f -> FilterChip(selectedFriend==f.id,{selectedFriend=f.id;wantId=""},label={Text(f.nickname)},modifier=Modifier.heightIn(min=56.dp)) }
                    } }
                    if(friend!=null) {
                        item { Text("내가 줄 장비 · 교환함에서 선택",color=Gold) }
                        items(snapshot?.inventory?.filter{!it.locked}?:emptyList(),key={"give:"+it.id}) { held ->
                            FilterChip(giveId==held.id,{giveId=held.id},label={Text(itemLabel(held.item,graph.balance))},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp))
                        }
                        item { Text("${friend.nickname}에게 받을 장비",color=Mint) }
                        items(friend.items.filter{!it.locked},key={"want:"+it.id}) { held ->
                            FilterChip(wantId==held.id,{wantId=held.id},label={Text(itemLabel(held.item,graph.balance))},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp))
                        }
                        item { ActionButton("선택한 장비로 교환 제안",{
                            if(give!=null&&want!=null)confirmation=Confirmation("${friend.nickname}에게 제안", "주는 장비: ${itemLabel(give.item,graph.balance)}\n받는 장비: ${itemLabel(want.item,graph.balance)}\n상대가 수락하면 교환함에서 두 장비가 바뀝니다. 제안은 24시간 후 만료됩니다."){controller.offer(friend.id,give.id,want.id)}
                        },enabled=!busy&&give!=null&&want!=null) }
                        if(friend.items.none{!it.locked})item{Text("친구의 교환 가능한 장비가 없어요.",color=Muted)}
                    } else item { Text("친구를 선택하고 두 사람의 장비를 골라 주세요. 먼저 교환함에 장비를 맡겨야 합니다.",color=Muted) }
                    item { HorizontalDivider();Text("받은 제안과 교환 기록",fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=12.dp)) }
                    items(snapshot?.trades?:emptyList(),key={it.id}) { trade -> CardBox(Modifier.fillMaxWidth()) {
                        Text("${trade.sender.nickname} → ${trade.recipient.nickname}",fontSize=20.sp,fontWeight=FontWeight.Bold)
                        Text("주는 장비: "+itemLabel(if(trade.incoming)trade.want else trade.give,graph.balance),color=Gold)
                        Text("받는 장비: "+itemLabel(if(trade.incoming)trade.give else trade.want,graph.balance),color=Mint)
                        Text(when(trade.status){"ACCEPTED"->"교환 완료 · 교환함에서 받을 수 있어요";"DECLINED"->"거절됨";"CANCELLED"->"취소됨 · 장비 상태가 바뀐 경우도 포함";"EXPIRED"->"기간 만료";else->"상대 수락 대기"},color=Muted)
                        if(trade.status=="PENDING") Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            if(trade.incoming) {
                                ActionButton("교환 수락",{confirmation=Confirmation("교환 수락", "내 장비 ${itemLabel(trade.want,graph.balance)}과 상대 장비 ${itemLabel(trade.give,graph.balance)}을 교환합니다."){controller.trade(trade.id,"accept")}},enabled=!busy)
                                TextButton({controller.trade(trade.id,"decline")},enabled=!busy,modifier=Modifier.heightIn(min=64.dp)){Text("거절")}
                            } else TextButton({controller.trade(trade.id,"cancel")},enabled=!busy,modifier=Modifier.heightIn(min=64.dp)){Text("제안 취소")}
                        }
                    } }
                }
            }
        }
    }
    ConfirmBox(confirmation){confirmation=null}
}
