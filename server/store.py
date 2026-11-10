"""Durable friendship, custody inventory, and atomic two-party exchanges."""
import hashlib
import json
import re
import secrets
import sqlite3
import time
import uuid
from contextlib import contextmanager
from pathlib import Path


class ApiError(Exception):
    def __init__(self, message, status=409):
        self.message, self.status = message, status


def identifier(value):
    if not isinstance(value, str) or not re.fullmatch(r"[a-zA-Z0-9_-]{1,80}", value):
        raise ApiError("올바르지 않은 요청 ID예요", 400)
    return value


class Store:
    def __init__(self, path, catalog):
        self.path, self.catalog = Path(path), set(catalog)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with self.connection() as db:
            db.execute("PRAGMA journal_mode=WAL")
            db.executescript("""
                CREATE TABLE IF NOT EXISTS users(id TEXT PRIMARY KEY, token_hash TEXT UNIQUE NOT NULL,
                    code TEXT UNIQUE NOT NULL, nickname TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS requests(id TEXT PRIMARY KEY, sender TEXT NOT NULL,
                    recipient TEXT NOT NULL, status TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS friends(a TEXT NOT NULL, b TEXT NOT NULL, PRIMARY KEY(a,b));
                CREATE TABLE IF NOT EXISTS deposits(id TEXT PRIMARY KEY, owner TEXT NOT NULL,
                    payload TEXT NOT NULL, status TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS items(id TEXT PRIMARY KEY, owner TEXT NOT NULL,
                    payload TEXT NOT NULL, state TEXT NOT NULL, locked_by TEXT);
                CREATE TABLE IF NOT EXISTS trades(id TEXT PRIMARY KEY, sender TEXT NOT NULL, recipient TEXT NOT NULL,
                    give_id TEXT NOT NULL, want_id TEXT NOT NULL, give_payload TEXT NOT NULL, want_payload TEXT NOT NULL,
                    status TEXT NOT NULL, created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL);
                CREATE TABLE IF NOT EXISTS deliveries(id TEXT PRIMARY KEY, owner TEXT NOT NULL,
                    item_id TEXT UNIQUE NOT NULL, payload TEXT NOT NULL, acknowledged INTEGER NOT NULL DEFAULT 0);
                CREATE INDEX IF NOT EXISTS items_owner ON items(owner,state);
                CREATE INDEX IF NOT EXISTS trades_sender ON trades(sender,status);
                CREATE INDEX IF NOT EXISTS trades_recipient ON trades(recipient,status);
            """)

    @contextmanager
    def connection(self):
        db = sqlite3.connect(self.path, timeout=15, isolation_level=None)
        db.row_factory = sqlite3.Row
        try:
            yield db
        finally:
            db.close()

    @contextmanager
    def transaction(self):
        with self.connection() as db:
            db.execute("BEGIN IMMEDIATE")
            try:
                now = int(time.time() * 1000)
                for row in db.execute("SELECT id FROM trades WHERE status='PENDING' AND expires_at<=?", (now,)).fetchall():
                    self.close_trade(db, row['id'], 'EXPIRED')
                yield db
                db.commit()
            except BaseException:
                db.rollback()
                raise

    @staticmethod
    def token_hash(token):
        if not re.fullmatch(r"[a-zA-Z0-9_-]{40,128}", token or ""):
            raise ApiError("친구 연결 정보가 필요해요", 401)
        return hashlib.sha256(token.encode()).hexdigest()

    def user(self, db, token):
        row = db.execute("SELECT * FROM users WHERE token_hash=?", (self.token_hash(token),)).fetchone()
        if not row:
            raise ApiError("먼저 친구 프로필을 연결해 주세요", 401)
        return row

    @staticmethod
    def profile(row):
        return {key: row[key] for key in ('id', 'code', 'nickname')}

    def register(self, token, nickname):
        if not isinstance(nickname, str) or not 1 <= len(nickname.strip()) <= 20 or any(ord(c) < 32 for c in nickname):
            raise ApiError("닉네임은 1~20자로 입력해 주세요", 400)
        token_hash = self.token_hash(token)
        with self.transaction() as db:
            row = db.execute("SELECT * FROM users WHERE token_hash=?", (token_hash,)).fetchone()
            if row:
                return self.profile(row)
            while True:
                code = ''.join(secrets.choice('ABCDEFGHJKLMNPQRSTUVWXYZ23456789') for _ in range(8))
                if not db.execute("SELECT 1 FROM users WHERE code=?", (code,)).fetchone():
                    break
            uid = uuid.uuid4().hex
            db.execute("INSERT INTO users VALUES(?,?,?,?)", (uid, token_hash, code, nickname.strip()))
            return {'id': uid, 'code': code, 'nickname': nickname.strip()}

    @staticmethod
    def are_friends(db, a, b):
        return db.execute("SELECT 1 FROM friends WHERE a=? AND b=?", tuple(sorted((a,b)))).fetchone() is not None

    def friend_request(self, db, uid, code):
        if not isinstance(code, str):
            raise ApiError("친구 코드를 확인해 주세요", 400)
        other = db.execute("SELECT id FROM users WHERE code=?", (code.strip().upper(),)).fetchone()
        if not other:
            raise ApiError("해당 친구 코드를 찾지 못했어요", 404)
        oid = other['id']
        if oid == uid:
            raise ApiError("자신에게 친구 요청을 보낼 수 없어요")
        if self.are_friends(db, uid, oid):
            return
        pending = db.execute("SELECT * FROM requests WHERE status='PENDING' AND ((sender=? AND recipient=?) OR (sender=? AND recipient=?))", (uid,oid,oid,uid)).fetchone()
        if pending:
            if pending['recipient'] == uid:
                raise ApiError("이미 받은 요청이 있어요. 받은 요청에서 수락해 주세요")
            return
        count = db.execute("SELECT COUNT(*) FROM requests WHERE sender=? AND status='PENDING'", (uid,)).fetchone()[0]
        if count >= 50:
            raise ApiError("대기 중인 친구 요청을 먼저 정리해 주세요")
        db.execute("INSERT INTO requests VALUES(?,?,?,'PENDING')", (uuid.uuid4().hex,uid,oid))

    def respond_friend(self, db, uid, rid, action):
        row = db.execute("SELECT * FROM requests WHERE id=?", (rid,)).fetchone()
        if not row or uid != row['sender' if action == 'cancel' else 'recipient']:
            raise ApiError("이 요청을 변경할 수 없어요", 403)
        target = {'accept':'ACCEPTED','decline':'DECLINED','cancel':'CANCELLED'}[action]
        if row['status'] == target:
            return
        if row['status'] != 'PENDING':
            raise ApiError("이미 처리된 친구 요청이에요")
        if action == 'accept':
            db.execute("INSERT OR IGNORE INTO friends VALUES(?,?)", tuple(sorted((row['sender'],row['recipient']))))
        db.execute("UPDATE requests SET status=? WHERE id=?", (target,rid))

    def validate_item(self, item):
        if not isinstance(item, dict) or set(item) != {'templateId','rarity','upgradeLevel'}:
            raise ApiError("장비 정보가 올바르지 않아요", 400)
        if not isinstance(item['templateId'],str) or not isinstance(item['rarity'],str) or item['templateId'] not in self.catalog or item['rarity'] not in ('COMMON','RARE','EPIC','LEGENDARY'):
            raise ApiError("지원하지 않는 장비예요", 400)
        if type(item['upgradeLevel']) is not int or not 0 <= item['upgradeLevel'] <= 10:
            raise ApiError("강화 수치가 올바르지 않아요", 400)
        return json.dumps(item, sort_keys=True, separators=(',',':'))

    def deposit(self, db, uid, body):
        did, payload = identifier(body.get('id')), self.validate_item(body.get('item'))
        previous = db.execute("SELECT * FROM deposits WHERE id=?", (did,)).fetchone()
        if previous:
            if previous['owner'] != uid:
                raise ApiError("전송 요청을 확인할 수 없어요", 403)
            if previous['status'] != 'CANCELLED' and previous['payload'] != payload:
                raise ApiError("같은 전송 ID의 장비가 달라요")
            return {'status':previous['status']}
        if db.execute("SELECT COUNT(*) FROM items WHERE owner=? AND state='AVAILABLE'", (uid,)).fetchone()[0] >= 100:
            raise ApiError("교환함이 가득 찼어요. 장비를 가방으로 받아 주세요")
        db.execute("INSERT INTO deposits VALUES(?,?,?,'COMMITTED')", (did,uid,payload))
        db.execute("INSERT INTO items VALUES(?,?,?,'AVAILABLE',NULL)", (did,uid,payload))
        return {'status':'COMMITTED'}

    def abort_deposit(self, db, uid, did):
        row = db.execute("SELECT * FROM deposits WHERE id=?", (did,)).fetchone()
        if row:
            if row['owner'] != uid:
                raise ApiError("전송 요청을 확인할 수 없어요", 403)
            return {'status':row['status']}
        # A durable tombstone also rejects a delayed deposit arriving after cancellation.
        db.execute("INSERT INTO deposits VALUES(?,?,'','CANCELLED')", (did,uid))
        return {'status':'CANCELLED'}

    @staticmethod
    def available(db, uid, iid):
        row = db.execute("SELECT * FROM items WHERE id=? AND owner=? AND state='AVAILABLE'", (iid,uid)).fetchone()
        if not row or row['locked_by']:
            raise ApiError("장비가 없거나 다른 교환에서 사용 중이에요")
        return row

    @staticmethod
    def close_trade(db, tid, status):
        db.execute("UPDATE trades SET status=? WHERE id=? AND status='PENDING'", (status,tid))
        db.execute("UPDATE items SET locked_by=NULL WHERE locked_by=?", (tid,))

    def cancel_related(self, db, item_ids, exclude=None):
        for row in db.execute("SELECT id,give_id,want_id FROM trades WHERE status='PENDING'").fetchall():
            if row['id'] != exclude and (row['give_id'] in item_ids or row['want_id'] in item_ids):
                self.close_trade(db, row['id'], 'CANCELLED')

    def offer(self, db, uid, body):
        tid, friend, give, want = [identifier(body.get(k)) for k in ('id','friendId','giveId','wantId')]
        existing = db.execute("SELECT * FROM trades WHERE id=?", (tid,)).fetchone()
        if existing:
            if (existing['sender'],existing['recipient'],existing['give_id'],existing['want_id']) != (uid,friend,give,want):
                raise ApiError("같은 교환 요청의 내용이 달라요")
            return
        if not self.are_friends(db, uid, friend):
            raise ApiError("친구가 수락한 뒤 교환할 수 있어요", 403)
        a, b = self.available(db,uid,give), self.available(db,friend,want)
        if a['payload'] == b['payload']:
            raise ApiError("같은 장비끼리는 교환할 수 없어요")
        now = int(time.time()*1000)
        db.execute("INSERT INTO trades VALUES(?,?,?,?,?,?,?,'PENDING',?,?)", (tid,uid,friend,give,want,a['payload'],b['payload'],now,now+86400000))
        db.execute("UPDATE items SET locked_by=? WHERE id=?", (tid,give))

    def trade_action(self, db, uid, tid, action):
        row = db.execute("SELECT * FROM trades WHERE id=?", (tid,)).fetchone()
        if not row or uid != row['sender' if action == 'cancel' else 'recipient']:
            raise ApiError("이 교환을 변경할 수 없어요", 403)
        target = {'accept':'ACCEPTED','decline':'DECLINED','cancel':'CANCELLED'}[action]
        if row['status'] == target:
            return
        if row['status'] != 'PENDING':
            raise ApiError("이미 종료된 교환이에요")
        if action != 'accept':
            self.close_trade(db, tid, target)
            return
        if not self.are_friends(db, row['sender'], row['recipient']):
            raise ApiError("친구 관계를 다시 확인해 주세요")
        give = db.execute("SELECT * FROM items WHERE id=?", (row['give_id'],)).fetchone()
        if not give or give['owner'] != row['sender'] or give['state'] != 'AVAILABLE' or give['locked_by'] != tid:
            raise ApiError("상대의 장비 상태가 바뀌었어요")
        self.available(db, uid, row['want_id'])
        db.execute("UPDATE items SET owner=?,locked_by=NULL WHERE id=?", (uid,row['give_id']))
        db.execute("UPDATE items SET owner=? WHERE id=?", (row['sender'],row['want_id']))
        self.close_trade(db,tid,'ACCEPTED')
        self.cancel_related(db,{row['give_id'],row['want_id']},tid)

    def withdraw(self, db, uid, iid):
        previous = db.execute("SELECT * FROM deliveries WHERE item_id=?", (iid,)).fetchone()
        if previous:
            if previous['owner'] != uid:
                raise ApiError("이 장비를 받을 수 없어요", 403)
            return
        item = self.available(db,uid,iid)
        db.execute("UPDATE items SET state='WITHDRAWN' WHERE id=?", (iid,))
        db.execute("INSERT INTO deliveries(id,owner,item_id,payload) VALUES(?,?,?,?)", (uuid.uuid4().hex,uid,iid,item['payload']))
        self.cancel_related(db,{iid})

    def state(self, db, uid):
        def inventory(owner):
            return [{'id':r['id'],'item':json.loads(r['payload']),'locked':r['locked_by'] is not None}
                    for r in db.execute("SELECT * FROM items WHERE owner=? AND state='AVAILABLE' ORDER BY rowid", (owner,))]
        own = self.profile(db.execute("SELECT * FROM users WHERE id=?", (uid,)).fetchone())
        friends = []
        for pair in db.execute("SELECT * FROM friends WHERE a=? OR b=?", (uid,uid)):
            other = pair['b'] if pair['a'] == uid else pair['a']
            profile = self.profile(db.execute("SELECT * FROM users WHERE id=?", (other,)).fetchone())
            friends.append({**profile,'items':inventory(other)})
        requests = []
        for r in db.execute("SELECT * FROM requests WHERE status='PENDING' AND (sender=? OR recipient=?)", (uid,uid)):
            other = r['recipient'] if r['sender'] == uid else r['sender']
            requests.append({'id':r['id'],'incoming':r['recipient']==uid,'person':self.profile(db.execute("SELECT * FROM users WHERE id=?", (other,)).fetchone())})
        trades = []
        for r in db.execute("SELECT * FROM trades WHERE sender=? OR recipient=? ORDER BY created_at DESC LIMIT 100", (uid,uid)):
            trades.append({'id':r['id'],'incoming':r['recipient']==uid,'status':r['status'],
                'sender':self.profile(db.execute("SELECT * FROM users WHERE id=?", (r['sender'],)).fetchone()),
                'recipient':self.profile(db.execute("SELECT * FROM users WHERE id=?", (r['recipient'],)).fetchone()),
                'give':json.loads(r['give_payload']),'want':json.loads(r['want_payload']),'expiresAt':r['expires_at']})
        deliveries = [{'id':r['id'],'item':json.loads(r['payload'])} for r in db.execute("SELECT * FROM deliveries WHERE owner=? AND acknowledged=0", (uid,))]
        return {'profile':own,'friends':friends,'requests':requests,'inventory':inventory(uid),'trades':trades,'deliveries':deliveries}

    def execute(self, method, path, token, body):
        if method == 'POST' and path == '/v1/profile':
            return self.register(token, body.get('nickname'))
        parts = path.strip('/').split('/')
        with self.transaction() as db:
            uid = self.user(db,token)['id']
            if method == 'GET' and path == '/v1/state':
                return self.state(db,uid)
            if method != 'POST':
                raise ApiError("지원하지 않는 요청이에요", 404)
            if path == '/v1/friends/request':
                self.friend_request(db,uid,body.get('code'))
            elif path == '/v1/deposits':
                return self.deposit(db,uid,body)
            elif path == '/v1/trades':
                self.offer(db,uid,body)
            elif len(parts) == 4 and parts[0] == 'v1':
                kind, rid, action = parts[1], identifier(parts[2]), parts[3]
                if kind == 'requests' and action in ('accept','decline','cancel'):
                    self.respond_friend(db,uid,rid,action)
                elif kind == 'trades' and action in ('accept','decline','cancel'):
                    self.trade_action(db,uid,rid,action)
                elif kind == 'deposits' and action == 'abort':
                    return self.abort_deposit(db,uid,rid)
                elif kind == 'items' and action == 'withdraw':
                    self.withdraw(db,uid,rid)
                elif kind == 'deliveries' and action == 'ack':
                    if not db.execute("SELECT 1 FROM deliveries WHERE id=? AND owner=?", (rid,uid)).fetchone():
                        raise ApiError("수령 기록을 확인할 수 없어요",403)
                    db.execute("UPDATE deliveries SET acknowledged=1 WHERE id=? AND owner=?", (rid,uid))
                elif kind == 'friends' and action == 'remove':
                    db.execute("DELETE FROM friends WHERE a=? AND b=?",tuple(sorted((uid,rid))))
                    for r in db.execute("SELECT id FROM trades WHERE status='PENDING' AND ((sender=? AND recipient=?) OR (sender=? AND recipient=?))", (uid,rid,rid,uid)).fetchall():
                        self.close_trade(db,r['id'],'CANCELLED')
                else:
                    raise ApiError("지원하지 않는 요청이에요",404)
            else:
                raise ApiError("지원하지 않는 요청이에요",404)
            return {'ok':True}
