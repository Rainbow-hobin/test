"""智能客服 RAG 测试台（Web 界面）

启动：.venv\\Scripts\\python web_app.py
打开：浏览器访问 http://127.0.0.1:8000

接口：
  GET  /         返回测试页面
  POST /api/ask  {"question": "问题", "top_k": 3} → {"answer": ..., "evidence": [...], "has_key": bool}
"""
import sys

sys.stdout.reconfigure(encoding="utf-8")

from fastapi import FastAPI
from fastapi.responses import HTMLResponse
from pydantic import BaseModel

from app import config
from app.vector_store import get_collection
from ask import retrieve, ask_llm

app = FastAPI(title="智能客服 RAG 测试台")


class AskRequest(BaseModel):
    question: str
    top_k: int = 3


@app.get("/", response_class=HTMLResponse)
def index():
    """返回测试页面（注入知识库条数与 Key 状态）。"""
    kb_count = get_collection().count()
    has_key = bool(config.DEEPSEEK_API_KEY)
    status_html = (
        '<span class="badge ok">● API Key 已配置</span>'
        if has_key
        else '<span class="badge warn">● API Key 未配置（仅检索）</span>'
    )
    return PAGE_TEMPLATE.replace("__KB_COUNT__", str(kb_count)).replace(
        "__STATUS__", status_html
    )


@app.post("/api/ask")
def ask(req: AskRequest):
    """检索 + 生成。未配置 Key 时只返回检索证据。"""
    question = req.question.strip()
    if not question:
        return {"answer": "请输入问题。", "evidence": [], "has_key": bool(config.DEEPSEEK_API_KEY)}

    items = retrieve(question, req.top_k)
    evidence = [
        {
            "section": it["meta"]["section"],
            "question": it["meta"]["question"],
            "distance": round(it["distance"], 4),
            "doc": it["doc"],
        }
        for it in items
    ]

    if not config.DEEPSEEK_API_KEY:
        answer = (
            "⚠️ 未配置 API Key（.env 中 DEEPSEEK_API_KEY 为空）。\n"
            "以上是检索到的知识证据。到 https://platform.deepseek.com 注册充值并创建 Key，"
            "填入 .env 后重启本服务，即可生成回答。"
        )
    else:
        try:
            answer = ask_llm(question, items)
        except Exception as e:  # 网络/鉴权/限流等
            answer = f"❌ 调用大模型失败：{type(e).__name__}: {e}"

    return {"answer": answer, "evidence": evidence, "has_key": bool(config.DEEPSEEK_API_KEY)}


# ================= 前端页面 =================

PAGE_TEMPLATE = """<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>智能客服 RAG 测试台</title>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    font-family: "Microsoft YaHei", system-ui, sans-serif;
    background: #f0f4f7; height: 100vh; display: flex; flex-direction: column;
  }
  header {
    background: #17324a; color: #fff; padding: 12px 20px;
    display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px;
  }
  header h1 { font-size: 17px; font-weight: 600; }
  .badges { display: flex; gap: 8px; align-items: center; font-size: 12px; }
  .badge {
    padding: 4px 10px; border-radius: 12px; font-size: 12px;
  }
  .badge.ok   { background: #1f7a4d; color: #fff; }
  .badge.warn { background: #b07f2c; color: #fff; }
  .badge.info { background: #e2e8f0; color: #334155; }
  #chat {
    flex: 1; overflow-y: auto; padding: 18px 16px;
    display: flex; flex-direction: column; gap: 14px; max-width: 780px; width: 100%;
    margin: 0 auto;
  }
  .row { display: flex; }
  .row.user { justify-content: flex-end; }
  .row.bot  { justify-content: flex-start; }
  .bubble {
    max-width: 76%; padding: 10px 14px; border-radius: 12px;
    font-size: 14px; line-height: 1.7; white-space: pre-wrap; word-break: break-word;
  }
  .row.user .bubble { background: #2563eb; color: #fff; border-bottom-right-radius: 4px; }
  .row.bot  .bubble { background: #fff; color: #1e293b; border: 1px solid #dbe4ec; border-bottom-left-radius: 4px; }
  .evidence-btn {
    margin-top: 6px; background: none; border: 1px solid #7ba6d4; color: #2c5482;
    border-radius: 12px; padding: 3px 10px; font-size: 12px; cursor: pointer;
  }
  .evidence-btn:hover { background: #e8f1fb; }
  .evidence {
    margin-top: 6px; font-size: 12px; color: #475569; line-height: 1.6;
    background: #f7f9fb; border: 1px dashed #9dbcdc; border-radius: 8px; padding: 8px 10px;
  }
  .evidence .ev-item { margin-bottom: 8px; }
  .evidence .ev-item:last-child { margin-bottom: 0; }
  .ev-tag { display: inline-block; background: #e8f1fb; color: #2c5482; border-radius: 4px; padding: 1px 6px; font-size: 11px; }
  .ev-dist { color: #b07f2c; font-size: 11px; }
  footer {
    background: #fff; border-top: 1px solid #dbe4ec; padding: 10px 16px;
    display: flex; gap: 10px; max-width: 780px; width: 100%; margin: 0 auto;
  }
  #input {
    flex: 1; border: 1px solid #cbd5e1; border-radius: 20px; padding: 9px 16px;
    font-size: 14px; outline: none; font-family: inherit;
  }
  #input:focus { border-color: #2563eb; }
  #send {
    background: #2563eb; color: #fff; border: none; border-radius: 20px;
    padding: 9px 22px; font-size: 14px; cursor: pointer;
  }
  #send:hover { background: #1d4ed8; }
  .hint { text-align: center; color: #94a3b8; font-size: 12px; padding: 4px 0; }
</style>
</head>
<body>
<header>
  <h1>智能客服 RAG 测试台</h1>
  <div class="badges">
    <span class="badge info">知识库 __KB_COUNT__ 条</span>
    __STATUS__
  </div>
</header>
<div id="chat">
  <div class="hint">试试问："支持七天无理由退货吗"、"怎么开发票"、"床垫多久发货"</div>
</div>
<footer>
  <input id="input" type="text" placeholder="输入你的问题，回车发送…" autocomplete="off">
  <button id="send">发送</button>
</footer>
<script>
  const chat = document.getElementById("chat");
  const input = document.getElementById("input");
  const send = document.getElementById("send");

  function addRow(role, text) {
    const row = document.createElement("div");
    row.className = "row " + role;
    const bubble = document.createElement("div");
    bubble.className = "bubble";
    bubble.textContent = text; // textContent：防止注入
    row.appendChild(bubble);
    chat.appendChild(row);
    chat.scrollTop = chat.scrollHeight;
    return bubble;
  }

  function addEvidence(evidence) {
    if (!evidence || !evidence.length) return;
    const wrap = document.createElement("div");
    const btn = document.createElement("button");
    btn.className = "evidence-btn";
    btn.textContent = "查看检索到的知识（" + evidence.length + " 条）";
    btn.onclick = function () {
      panel.style.display = panel.style.display === "none" ? "block" : "none";
    };
    const panel = document.createElement("div");
    panel.className = "evidence";
    panel.style.display = "none";
    evidence.forEach(function (e) {
      const item = document.createElement("div");
      item.className = "ev-item";
      const tag = document.createElement("span");
      tag.className = "ev-tag";
      tag.textContent = e.section;
      const dist = document.createElement("span");
      dist.className = "ev-dist";
      dist.textContent = " 距离 " + e.distance;
      const q = document.createElement("div");
      q.textContent = "Q: " + e.question;
      const d = document.createElement("div");
      d.textContent = e.doc;
      item.appendChild(tag);
      item.appendChild(dist);
      item.appendChild(q);
      item.appendChild(d);
      panel.appendChild(item);
    });
    wrap.appendChild(btn);
    wrap.appendChild(panel);
    chat.appendChild(wrap);
    chat.scrollTop = chat.scrollHeight;
  }

  async function ask() {
    const q = input.value.trim();
    if (!q) return;
    input.value = "";
    addRow("user", q);
    const bot = addRow("bot", "正在检索知识库…");
    try {
      const res = await fetch("/api/ask", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ question: q, top_k: 3 })
      });
      const data = await res.json();
      bot.textContent = data.answer;
      addEvidence(data.evidence);
    } catch (err) {
      bot.textContent = "❌ 请求失败：" + err;
    }
  }

  send.onclick = ask;
  input.addEventListener("keydown", function (e) {
    if (e.key === "Enter") ask();
  });
</script>
</body>
</html>
"""

if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="127.0.0.1", port=8000)
