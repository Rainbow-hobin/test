"""Chroma 向量库封装：入库、清空、检索。"""
import chromadb

from . import config

_client = None


def get_client() -> chromadb.Client:
    global _client
    if _client is None:
        _client = chromadb.PersistentClient(path=config.CHROMA_DIR)
    return _client


def get_collection():
    """取（或建）知识库集合。"""
    return get_client().get_or_create_collection(name="knowledge_base")


def reset_collection():
    """清空旧库并重建，保证 ingest 幂等（重复跑不会越积越多）。"""
    client = get_client()
    try:
        client.delete_collection("knowledge_base")
    except Exception:
        pass
    return client.get_or_create_collection(name="knowledge_base")


def add_chunks(chunks: list[dict], vectors: list[list[float]]) -> int:
    """把切分后的条目连同向量一起入库。"""
    col = reset_collection()
    ids = [f"{c['section']}-{i}" for i, c in enumerate(chunks)]
    docs = [c["content"] for c in chunks]
    metas = [{"section": c["section"], "question": c["q"]} for c in chunks]
    col.add(ids=ids, documents=docs, embeddings=vectors, metadatas=metas)
    return len(ids)


def search(query_vector: list[float], top_k: int = 3) -> dict:
    """按向量相似度检索，返回 Chroma 的原始查询结果。"""
    col = get_collection()
    return col.query(query_embeddings=[query_vector], n_results=top_k)
