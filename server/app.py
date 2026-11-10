"""Small ASGI API. Run behind HTTPS when connecting over the internet."""
import asyncio
from collections import defaultdict, deque
import json
import os
from pathlib import Path
import sqlite3
import time
from store import ApiError, Store

root = Path(__file__).resolve().parents[1]
catalog = json.loads((Path(__file__).parent/'catalog.json').read_text())
store = Store(os.environ.get('BOSS_SOCIAL_DB', str(root/'work/social-server.sqlite')), catalog)
requests = defaultdict(deque)


async def app(scope, receive, send):
    if scope['type'] != 'http':
        return
    status = 200
    try:
        path, method = scope['path'], scope['method']
        if path == '/health' and method == 'GET':
            result = {'service':'expedition-boss-social','version':'0.2.0'}
        else:
            ip = (scope.get('client') or ('unknown',))[0]
            now = time.monotonic()
            bucket = requests[ip]
            while bucket and bucket[0] < now-60:
                bucket.popleft()
            if len(bucket) >= 240:
                raise ApiError('요청이 많아요. 잠시 후 다시 시도해 주세요',429)
            bucket.append(now)
            if len(requests) > 10000:
                for key in list(requests):
                    if not requests[key] or requests[key][-1] < now-60:
                        del requests[key]
            headers = dict(scope['headers'])
            auth = headers.get(b'authorization',b'').decode('ascii',errors='ignore')
            token = auth.removeprefix('Bearer ') if auth.startswith('Bearer ') else ''
            body = bytearray()
            while True:
                event = await receive()
                if event['type'] == 'http.disconnect':
                    return
                body.extend(event.get('body',b''))
                if len(body) > 16384:
                    raise ApiError('요청이 너무 커요',413)
                if not event.get('more_body'):
                    break
            try:
                data = json.loads(body) if body else {}
            except (ValueError,UnicodeDecodeError):
                raise ApiError('요청 형식이 올바르지 않아요',400)
            if not isinstance(data,dict):
                raise ApiError('요청 형식이 올바르지 않아요',400)
            result = await asyncio.to_thread(store.execute,method,path,token,data)
    except ApiError as e:
        status, result = e.status, {'error':e.message}
    except sqlite3.Error:
        status, result = 503, {'error':'저장소가 바빠요. 잠시 후 다시 시도해 주세요'}
    except Exception:
        status, result = 500, {'error':'요청을 처리하지 못했어요. 다시 연결해 주세요'}
    payload = json.dumps(result,ensure_ascii=False).encode('utf-8')
    await send({'type':'http.response.start','status':status,'headers':[
        (b'content-type',b'application/json; charset=utf-8'),(b'cache-control',b'no-store'),
        (b'x-content-type-options',b'nosniff'),(b'content-length',str(len(payload)).encode())]})
    await send({'type':'http.response.body','body':payload})
