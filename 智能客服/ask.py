"""第 2 步：提问（RAG 主流程）。

流程：问题向量化 → 向量库检索 top-k → 拼 Prompt → 大模型生成回答

用法：
  .venv\\Scripts\\python ask.py                  # 交互式问答（需 .env 里配好 API Key）
  .venv\\Scripts\\python ask.py --no-llm        # 只检索不调用大模型（验证检索用，不需要 Key）
"""
import argparse
import sys

sys.stdout.reconfigure(encoding="utf-8")

from app import config
from app.embedder import embed_texts
from app.vector_store import search


def retrieve(query: str, top_k: int) -> list[dict]:
    """检索知识库，返回按相似度排序的条目列表。"""
    qv = embed_texts([query])[0]
    res = search(qv, top_k)
    items = []
    for i in range(len(res["ids"][0])):
        items.append(
            {
                "id": res["ids"][0][i],
                "doc": res["documents"][0][i],
                "meta": res["metadatas"][0][i],
                "distance": res["distances"][0][i],
            }
        )
    return items


def build_prompt(query: str, items: list[dict]) -> str:
    """把检索到的知识组装成给大模型的 Prompt。"""
    lines = [
        "你是一个电商客服助手。请只依据下面提供的「参考知识」回答用户问题。",
        "要求：",
        "1. 回答准确、友好、简洁，直接给用户答案；",
        "2. 如果参考知识不足以回答，请明确回复「这个问题我需要转人工处理」，不要编造；",
        "3. 不要提及「参考知识」「检索」等内部概念。",
        "",
        "参考知识：",
    ]
    for i, item in enumerate(items, 1):
        lines.append(f"{i}. {item['doc']}")
    lines.append("")
    lines.append(f"用户问题：{query}")
    return "\n".join(lines)


def ask_llm(query: str, items: list[dict]) -> str:
    """调用大模型生成回答（DeepSeek，OpenAI 兼容协议）。"""
    from openai import OpenAI

    client = OpenAI(api_key=config.DEEPSEEK_API_KEY, base_url=config.LLM_BASE_URL)
    resp = client.chat.completions.create(
        model=config.LLM_MODEL,
        messages=[{"role": "user", "content": build_prompt(query, items)}],
        temperature=0.3,
    )
    return resp.choices[0].message.content


def print_evidence(items: list[dict]):
    """打印检索到的证据（debug 用）。"""
    print(f"\n[检索到 {len(items)} 条相关知识，按相似度排序]")
    for it in items:
        q = it["meta"].get("question", "")
        print(f"  · [{it['meta']['section']}] {q}  (距离 {it['distance']:.4f})")


def one_shot(query: str, top_k: int, use_llm: bool):
    """单次问答（供 --no-llm 或测试调用）。"""
    items = retrieve(query, top_k)
    print_evidence(items)
    if not use_llm:
        return
    answer = ask_llm(query, items)
    print(f"\n客服：{answer}")


def interactive(top_k: int):
    """交互式问答循环。"""
    print("客服助手已就绪（输入问题回车提问，输入 exit 退出）\n")
    while True:
        try:
            query = input("你：").strip()
        except (EOFError, KeyboardInterrupt):
            break
        if not query or query.lower() in ("exit", "quit", "退出", "q"):
            break
        items = retrieve(query, top_k)
        print_evidence(items)
        answer = ask_llm(query, items)
        print(f"客服：{answer}\n")


def main():
    parser = argparse.ArgumentParser(description="RAG 问答入口")
    parser.add_argument("--top-k", type=int, default=config.TOP_K, help="检索条数（默认 3）")
    parser.add_argument("--no-llm", action="store_true", help="只检索不调用大模型（不需要 API Key）")
    parser.add_argument("query", nargs="?", default=None, help="可选：直接传入问题，不传则进入交互模式")
    args = parser.parse_args()

    if args.no_llm:
        q = args.query or input("请输入问题：")
        one_shot(q, args.top_k, use_llm=False)
        return

    if not config.DEEPSEEK_API_KEY:
        print("❌ 未配置 DEEPSEEK_API_KEY，无法调用大模型。")
        print("   1) 到 https://platform.deepseek.com 注册充值，创建 API Key；")
        print("   2) 把 Key 填进项目根目录的 .env 文件（参考 .env.example）；")
        print("   3) 重新运行本脚本。")
        print("   想先验证检索效果（不需要 Key）：python ask.py --no-llm")
        return

    if args.query:
        one_shot(args.query, args.top_k, use_llm=True)
    else:
        interactive(args.top_k)


if __name__ == "__main__":
    main()
