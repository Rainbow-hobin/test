"""LLM tool-calling 模块：定义点餐工具 + system prompt + LLM 调用循环。"""
import json
from openai import OpenAI
from app import config

# ---- 工具定义（OpenAI function calling 格式） ----
TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "get_menu",
            "description": "获取当前在售菜品列表。返回菜名、价格。用户问有什么菜、想看菜单时调用。",
            "parameters": {"type": "object", "properties": {}}
        }
    },
    {
        "type": "function",
        "function": {
            "name": "add_to_cart",
            "description": "将菜品加入购物车。dish_name 用用户原话即可（如“宫爆鸡丁”），内部会模糊匹配到真实菜名。quantity 默认1。",
            "parameters": {
                "type": "object",
                "properties": {
                    "dish_name": {"type": "string", "description": "菜品名称（用户口语表达）"},
                    "quantity": {"type": "integer", "description": "份数", "default": 1}
                },
                "required": ["dish_name"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "get_addresses",
            "description": "获取用户收货地址列表。下单前需要让用户选择地址时调用。",
            "parameters": {"type": "object", "properties": {}}
        }
    },
    {
        "type": "function",
        "function": {
            "name": "submit_order",
            "description": "提交订单。调用前必须已和用户确认了菜品和地址。address_id 是地址编号。",
            "parameters": {
                "type": "object",
                "properties": {
                    "address_id": {"type": "integer", "description": "收货地址ID"}
                },
                "required": ["address_id"]
            }
        }
    },
    {
        "type": "function",
        "function": {
            "name": "clear_cart",
            "description": "清空购物车。",
            "parameters": {"type": "object", "properties": {}}
        }
    },
    {
        "type": "function",
        "function": {
            "name": "get_cart",
            "description": "查看当前购物车内容。",
            "parameters": {"type": "object", "properties": {}}
        }
    }
]

SYSTEM_PROMPT = """你是一个外卖点餐助手，通过工具帮用户点餐、查看菜单、管理购物车、提交订单。

规则：
1. 用户说菜名时，用 add_to_cart 加入购物车，dish_name 直接用用户的原话，不要修正。
2. 下单前必须先调 get_addresses 让用户选择地址。
3. 提交订单前，先用 get_cart 展示购物车内容，和用户确认后再调 submit_order。
4. 用户问营业时间、退款政策等非点餐问题：如果知识库能回答就回答，不能就回复"这个问题我需要转人工处理"。
5. 用户要求转人工：回复"好的，正在为您转接人工客服"，不要再调工具。
6. 回复要简短友好，像真人客服。
"""


def call_llm(messages):
    """调用 DeepSeek，返回 chat completion 响应。"""
    client = OpenAI(api_key=config.DEEPSEEK_API_KEY, base_url=config.LLM_BASE_URL)
    resp = client.chat.completions.create(
        model=config.LLM_MODEL,
        messages=messages,
        tools=TOOLS,
        temperature=0.3,
    )
    return resp
