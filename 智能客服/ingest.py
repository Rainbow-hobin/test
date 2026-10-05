"""第 1 步：构建知识库向量库。

流程：读 data/knowledge_base.md → 切分 → 向量化 → 存入本地向量库 .chroma/
用法：.venv\\Scripts\\python ingest.py
"""
import sys

sys.stdout.reconfigure(encoding="utf-8")

from app import config
from app.embedder import embed_texts
from app.splitter import split_knowledge_base
from app.vector_store import add_chunks


def main():
    text = config.KB_FILE.read_text(encoding="utf-8")
    chunks = split_knowledge_base(text)
    print(f"[1/3] 读入 {config.KB_FILE.name}：共 {len(chunks)} 条问答")

    print("[2/3] 向量化（首次会下载 embedding 模型约 100MB，请耐心等待）...")
    contents = [c["content"] for c in chunks]
    vectors = embed_texts(contents)

    print("[3/3] 写入向量库...")
    n = add_chunks(chunks, vectors)
    print(f"✅ 入库完成：{n} 条，向量库目录：{config.CHROMA_DIR}")
    print("下一步：运行 ask.py 提问（.venv\\Scripts\\python ask.py）")


if __name__ == "__main__":
    main()
