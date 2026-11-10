import concurrent.futures
import json
from pathlib import Path
import secrets
import sys
import tempfile
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from store import Store, ApiError

CATALOG = json.loads((Path(__file__).resolve().parents[1]/'catalog.json').read_text())

class ExchangeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.store = Store(Path(self.temp.name)/'test.sqlite',CATALOG)
        self.a,self.b,self.c = [secrets.token_urlsafe(32) for _ in range(3)]
        self.people = {t:self.call(t,'/v1/profile',{'nickname':n}) for t,n in [(self.a,'A'),(self.b,'B'),(self.c,'C')]}
        self.first = {'templateId':CATALOG[0],'rarity':'RARE','upgradeLevel':3}
        self.second = {'templateId':CATALOG[1],'rarity':'EPIC','upgradeLevel':2}
    def tearDown(self): self.temp.cleanup()
    def call(self,token,path,data=None): return self.store.execute('POST',path,token,data or {})
    def state(self,token): return self.store.execute('GET','/v1/state',token,{})
    def befriend(self,a,b):
        self.call(a,'/v1/friends/request',{'code':self.people[b]['code']})
        request = next(r for r in self.state(b)['requests'] if r['person']['id']==self.people[a]['id'])
        self.call(b,'/v1/requests/'+request['id']+'/accept')
    def deposit(self,token,did,item): return self.call(token,'/v1/deposits',{'id':did,'item':item})
    def trade(self,tid='trade1',a=None,b=None,give='a-item',want='b-item'):
        a,b = a or self.a,b or self.b
        self.call(a,'/v1/trades',{'id':tid,'friendId':self.people[b]['id'],'giveId':give,'wantId':want})
    def prepare(self):
        self.befriend(self.a,self.b)
        self.deposit(self.a,'a-item',self.first)
        self.deposit(self.b,'b-item',self.second)
        self.trade()
    def test_friendship_needs_recipient_consent(self):
        self.call(self.a,'/v1/friends/request',{'code':self.people[self.b]['code']})
        self.assertEqual([],self.state(self.a)['friends'])
        rid=self.state(self.b)['requests'][0]['id']
        with self.assertRaises(ApiError):self.call(self.a,'/v1/requests/'+rid+'/accept')
        self.call(self.b,'/v1/requests/'+rid+'/accept')
        self.assertEqual(1,len(self.state(self.a)['friends']))
    def test_unrelated_users_cannot_see_inventory(self):
        self.deposit(self.a,'secret-item',self.first)
        self.assertEqual([],self.state(self.c)['friends'])
        with self.assertRaises(ApiError):self.call(self.c,'/v1/items/secret-item/withdraw')
        with self.assertRaises(ApiError):self.state('invalid')
    def test_repeated_deposit_is_one_item_and_payload_bound(self):
        self.deposit(self.a,'a-item',self.first)
        self.deposit(self.a,'a-item',self.first)
        self.assertEqual(1,len(self.state(self.a)['inventory']))
        with self.assertRaises(ApiError):self.deposit(self.a,'a-item',self.second)
        with self.assertRaises(ApiError):self.deposit(self.b,'a-item',self.first)
    def test_abort_prevents_delayed_deposit(self):
        self.call(self.a,'/v1/deposits/delayed/abort')
        self.assertEqual('CANCELLED',self.deposit(self.a,'delayed',self.first)['status'])
        self.assertEqual([],self.state(self.a)['inventory'])
    def test_lost_deposit_response_recovers_after_restart(self):
        self.deposit(self.a,'a-item',self.first)
        self.store=Store(self.store.path,CATALOG)
        self.assertEqual('COMMITTED',self.call(self.a,'/v1/deposits/a-item/abort')['status'])
        self.assertEqual('COMMITTED',self.deposit(self.a,'a-item',self.first)['status'])
        self.assertEqual(1,len(self.state(self.a)['inventory']))
    def test_trade_is_atomic_and_preserves_upgrades(self):
        self.prepare()
        with self.assertRaises(ApiError):self.call(self.a,'/v1/items/a-item/withdraw')
        with self.assertRaises(ApiError):self.call(self.c,'/v1/trades/trade1/accept')
        self.call(self.b,'/v1/trades/trade1/accept')
        self.call(self.b,'/v1/trades/trade1/accept')
        self.assertEqual(self.second,self.state(self.a)['inventory'][0]['item'])
        self.assertEqual(self.first,self.state(self.b)['inventory'][0]['item'])
    def test_accept_cancel_race_has_one_terminal_outcome(self):
        self.prepare()
        def perform(args):
            try:self.call(*args)
            except ApiError:pass
        with concurrent.futures.ThreadPoolExecutor(2) as pool:
            list(pool.map(perform,[(self.b,'/v1/trades/trade1/accept'),(self.a,'/v1/trades/trade1/cancel')]))
        state=self.state(self.a)
        status=state['trades'][0]['status']
        self.assertIn(status,['ACCEPTED','CANCELLED'])
        self.assertEqual(1,len(state['inventory']))
        self.assertEqual(1,len(self.state(self.b)['inventory']))
        self.assertEqual(self.second if status=='ACCEPTED' else self.first,state['inventory'][0]['item'])
    def test_competing_offers_cannot_spend_same_item_twice(self):
        self.prepare()
        self.befriend(self.c,self.b)
        self.deposit(self.c,'c-item',self.first)
        self.trade('trade2',self.c,self.b,'c-item','b-item')
        self.call(self.b,'/v1/trades/trade1/accept')
        with self.assertRaises(ApiError):self.call(self.b,'/v1/trades/trade2/accept')
        self.assertFalse(self.state(self.c)['inventory'][0]['locked'])
    def test_decline_releases_only_sender_reservation(self):
        self.prepare()
        self.call(self.b,'/v1/trades/trade1/decline')
        self.assertFalse(self.state(self.a)['inventory'][0]['locked'])
        self.assertEqual(self.first,self.state(self.a)['inventory'][0]['item'])
    def test_withdrawal_delivery_persists_until_ack(self):
        self.deposit(self.a,'a-item',self.first)
        self.call(self.a,'/v1/items/a-item/withdraw')
        self.call(self.a,'/v1/items/a-item/withdraw')
        self.store=Store(self.store.path,CATALOG)
        deliveries=self.state(self.a)['deliveries']
        self.assertEqual(1,len(deliveries));self.assertEqual([],self.state(self.a)['inventory'])
        with self.assertRaises(ApiError):self.call(self.b,'/v1/deliveries/'+deliveries[0]['id']+'/ack')
        self.call(self.a,'/v1/deliveries/'+deliveries[0]['id']+'/ack')
        self.deposit(self.a,'a-item',self.first)
        self.assertEqual([],self.state(self.a)['inventory'])
        self.assertEqual([],self.state(self.a)['deliveries'])
    def test_friend_removal_cancels_pending_trade(self):
        self.prepare()
        self.call(self.a,'/v1/friends/'+self.people[self.b]['id']+'/remove')
        self.assertEqual([],self.state(self.b)['friends'])
        self.assertEqual('CANCELLED',self.state(self.a)['trades'][0]['status'])
        self.assertFalse(self.state(self.a)['inventory'][0]['locked'])
    def test_expiry_unlocks_items(self):
        self.prepare()
        with self.store.connection() as db:db.execute("UPDATE trades SET expires_at=0")
        self.assertEqual('EXPIRED',self.state(self.a)['trades'][0]['status'])
        self.assertFalse(self.state(self.a)['inventory'][0]['locked'])
    def test_unknown_items_and_upgrade_values_are_rejected(self):
        for item in [dict(self.first,upgradeLevel=-1),dict(self.first,upgradeLevel=True),dict(self.first,templateId='invented'),dict(self.first,rarity='MONEY')]:
            with self.assertRaises(ApiError):self.deposit(self.a,'bad',item)
        self.assertEqual([],self.state(self.a)['inventory'])
    def test_server_catalog_matches_game(self):
        balance=Path(__file__).resolve().parents[2]/'android/app-boss/src/main/assets/balance.json'
        self.assertEqual(set(CATALOG),{x['id'] for x in json.loads(balance.read_text(encoding='utf-8-sig'))['equipment']})

if __name__=='__main__':unittest.main()
