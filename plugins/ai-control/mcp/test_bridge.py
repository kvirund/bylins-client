"""
Регрессионный тест MCP-моста.

Поднимает фейковый HTTP-сервер вместо плагина и ведёт с мостом настоящий
MCP-диалог по stdio: так проверяется и протокол, и трансляция в HTTP, без
запуска клиента.

Запуск:  python plugins/ai-control/mcp/test_bridge.py
"""
import json, os, subprocess, sys, threading
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT = 47471
calls = []

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode() if length else "{}"
        calls.append((self.path, dict(self.headers), json.loads(body or "{}")))
        path = self.path
        if path == "/session/open":
            if self.headers.get("X-Master-Token") != "MASTER":
                return self.reply(401, {"error": "Неверный мастер-токен"})
            return self.reply(200, {"id": "s1", "token": "SESS", "cursor": 1, "hasWriteLease": True})
        if self.headers.get("X-Session-Token") != "SESS" and path != "/status":
            return self.reply(401, {"error": "Сессия не найдена или закрыта"})
        if path == "/output":
            return self.reply(200, {"lines": ["Вы стоите на площади.", "Выходы: север"], "missed": 0, "nextSeq": 3})
        if path == "/exec":
            return self.reply(200, {"lines": ["Вы идете на север.", "Тёмный лес."], "stoppedBy": "quiet"})
        if path == "/status":
            return self.reply(200, {"connected": True, "sessions": [{"id": "s1", "name": "claude"}]})
        if path.startswith("/client/"):
            return self.reply(200, {"ok": True, "action": path})
        if path == "/session/close":
            return self.reply(200, {"closed": True})
        return self.reply(404, {"error": "нет такого"})
    def reply(self, code, payload):
        data = json.dumps(payload, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

server = HTTPServer(("127.0.0.1", PORT), Handler)
threading.Thread(target=server.serve_forever, daemon=True).start()

env = dict(os.environ,
           BYLINS_AI_URL=f"http://127.0.0.1:{PORT}",
           BYLINS_AI_MASTER_TOKEN="MASTER",
           BYLINS_AI_NAME="claude")
bridge = os.path.join(r"C:\dev\games\bylins-client\plugins\ai-control\mcp", "bylins_mcp.py")
proc = subprocess.Popen([sys.executable, bridge], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                        stderr=subprocess.PIPE, env=env, text=True, encoding="utf-8", bufsize=1)

def rpc(obj):
    proc.stdin.write(json.dumps(obj) + "\n"); proc.stdin.flush()
    if "id" not in obj: return None
    return json.loads(proc.stdout.readline())

ok = True
def check(label, cond, extra=""):
    global ok
    print(("PASS " if cond else "FAIL ") + label + ("" if cond else " :: " + str(extra)))
    ok = ok and cond

r = rpc({"jsonrpc":"2.0","id":1,"method":"initialize","params":{}})
check("initialize", r["result"]["protocolVersion"] == "2024-11-05", r)
rpc({"jsonrpc":"2.0","method":"notifications/initialized"})

r = rpc({"jsonrpc":"2.0","id":2,"method":"tools/list"})
names = [t["name"] for t in r["result"]["tools"]]
check("tools/list", set(names) == {"mud_read","mud_exec","mud_status","mud_client","mud_close"}, names)

r = rpc({"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"mud_read","arguments":{"limit":50}}})
text = r["result"]["content"][0]["text"]
check("mud_read содержит вывод", "площади" in text, text)
check("сессия открылась лениво", any(p == "/session/open" for p, _, _ in calls), calls)

r = rpc({"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"mud_exec","arguments":{"commands":["север"]}}})
check("mud_exec вернул ответ", "Тёмный лес" in r["result"]["content"][0]["text"], r)
exec_call = [c for c in calls if c[0] == "/exec"][0]
check("exec шлёт токен сессии", exec_call[1].get("X-Session-Token") == "SESS", exec_call[1])
check("exec шлёт команды", exec_call[2]["commands"] == ["север"], exec_call[2])

r = rpc({"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"mud_client","arguments":{"action":"triggers/create","params":{"pattern":"x","commands":["y"]}}}})
check("mud_client маршрутизирует", "triggers/create" in r["result"]["content"][0]["text"], r)

r = rpc({"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"mud_status","arguments":{}}})
check("mud_status", '"connected": true' in r["result"]["content"][0]["text"], r)

r = rpc({"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"mud_client","arguments":{}}})
check("ошибка инструмента не рвёт связь", r["result"].get("isError") is True, r)

r = rpc({"jsonrpc":"2.0","id":8,"method":"tools/call","params":{"name":"mud_close","arguments":{}}})
check("mud_close", "закрыт" in r["result"]["content"][0]["text"], r)

r = rpc({"jsonrpc":"2.0","id":9,"method":"unknown/method"})
check("неизвестный метод → JSON-RPC error", r.get("error", {}).get("code") == -32601, r)

proc.stdin.close(); proc.wait(timeout=10)
server.shutdown()
print("\nRESULT:", "ALL PASS" if ok else "FAILURES")
sys.exit(0 if ok else 1)
