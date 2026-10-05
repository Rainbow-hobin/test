"""配置入口：统一从 .env 读取所有配置。"""
import os
from pathlib import Path

from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parent.parent
load_dotenv(BASE_DIR / ".env")

# ---- LLM（大模型生成） ----
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "").strip()
LLM_BASE_URL = os.getenv("LLM_BASE_URL", "https://api.deepseek.com").strip()
LLM_MODEL = os.getenv("LLM_MODEL", "deepseek-chat").strip()

# ---- 检索 ----
CHROMA_DIR = os.getenv("CHROMA_DIR", str(BASE_DIR / ".chroma")).strip()
TOP_K = int(os.getenv("TOP_K", "3"))

# ---- 知识库文件 ----
KB_FILE = BASE_DIR / "data" / "knowledge_base.md"
