# 智能客服 Agent —— RAG 最小闭环（Python）

基于 **RAG（检索增强生成）** 的本地知识问答最小实现：



```
用户问题 → 向量化 → 向量库检索 top-k → 拼 Prompt → 大模型生成有据回答
```

## 项目结构



```
E:\kaifa\智能客服\
├── .env                  # 你的配置（API Key 填这里）※已创建，key 待填
├── .env.example          # 配置模板（对照参考）
├── requirements.txt      # 依赖清单
├── ingest.py             # 第1步：知识库入库（切分→向量化→存库）
├── ask.py                # 第2步：提问（检索→生成）
├── web_app.py            # 网页测试台（FastAPI + 聊天界面）
├── start_web.bat         # 一键启动测试台
├── app/
│   ├── config.py         # 配置读取
│   ├── splitter.py       # 语料切分器
│   ├── embedder.py       # 本地 Embedding（bge-small-zh，无需 API Key）
│   └── vector_store.py   # Chroma 向量库封装
├── data/
│   └── knowledge_base.md # 知识库语料（示例电商 FAQ，可整体替换）
└── .venv/                # 项目虚拟环境（Python 3.13）
```

## 一、环境准备（已完成 ✅）



* 虚拟环境：`.venv`（基于 Anaconda Python 3.13 创建，避开 3.14 的兼容性风险）

* 依赖：chromadb（向量库）/fastembed（本地 Embedding）/openai（调大模型）/python-dotenv

## 二、第一步：构建向量库



```
cd E:\kaifa\智能客服
.venv\Scripts\python ingest.py
```

首次运行会自动下载 Embedding 模型（约 100MB，走国内镜像），随后把 `data/knowledge_base.md` 里的问答切分、向量化、入库。

## 三、第二步：提问

### 3.1 没有 API Key 时：先验证检索（✅ 我现在就能跑通）



```
.venv\Scripts\python ask.py --no-llm
```

只做 "检索" 不调大模型，会打印检索到的知识条目和相似度距离。

### 3.2 配置 API Key，跑通完整闭环



1. 到 [https://platform.deepseek.com](https://platform.deepseek.com) 注册、充值（几块钱够跑很久）

2. 「API Keys」→ 创建，复制 `sk-` 开头的 Key

3. 打开项目根目录的 `.env`，把 Key 填到 `DEEPSEEK_API_KEY=` 后面

4. 运行：



```
.venv\Scripts\python ask.py
```

进入交互问答（输入问题回车，输入 `exit` 退出）。

## 四、网页测试台（Web 界面）

双击 `start_web.bat`（或运行 `.venv\Scripts\python web_app.py`），浏览器打开 <http://127.0.0.1:8000>：

- 聊天式界面，输入问题回车即测
- 每条回答下方可展开「查看检索到的知识」：显示命中的板块、问题、相似度距离、原文证据
- 顶部显示 API Key 状态：未配置 Key 时只返回检索证据；配置后（填 `.env`）自动出生成回答，无需改代码

## 五、换成你自己的知识库

把真实 FAQ / 手册 / 政策文档整理成 `data/knowledge_base.md` 的格式，重新跑一次 `ingest.py` 即可：



```
## 板块名
Q: 问题
A: 答案
```

支持的问题格式：按 `## 板块` 分组、`Q:` 开头的问题 + `A:` 开头的答案，答案可多行续写。

## 六、每个模块在干什么（原理速览）



| 模块        | 文件                      | 作用             | 比喻                  |
| --------- | ----------------------- | -------------- | ------------------- |
| 切分器       | `app/splitter.py`       | 把长文档切成一条条问答    | 把整本书拆成便利贴           |
| Embedding | `app/embedder.py`       | 把文字变成向量（语义数字化） | 给每张便利贴编号码           |
| 向量库       | `app/vector_store.py`   | 存向量、按相似度检索     | 书架 + 自动找最相关的便利贴     |
| Prompt 组装 | `ask.py build_prompt()` | 检索结果 + 问题 拼成指令 | 给 AI 递上 "参考资料 + 考题" |
| 大模型       | `ask.py ask_llm()`      | 依据资料生成回答       | 阅卷老师照着资料答题          |

## 七、常见问题



* **模型下载慢 / 失败**：代码已默认走国内镜像（`HF_ENDPOINT=https://hf-mirror.com`），重跑 `ingest.py` 即可

* **中文乱码**：脚本已处理控制台编码；如仍乱码，先执行 `chcp 65001` 再运行

* **换大模型**：改 `.env` 里的 `LLM_BASE_URL` 和 `LLM_MODEL`（通义 / 智谱 / OpenAI 都兼容 OpenAI 协议）

## 八、下一步（结合 Agent 的方向）



1. 加多轮会话记忆（LangChain 或自己存历史）

2. 加工具调用：查订单 / 退换货（意图路由 + function calling）

3. 建评估集，每次改 Prompt 后回归对比