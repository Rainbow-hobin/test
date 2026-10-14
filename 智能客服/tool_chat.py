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

SYSTEM_PROMPT = """你是外卖点餐助手。以下情况必须调用对应工具，严禁只用文字回复：

- 用户提到菜名（如"帮我点2份宫保鸡丁""来一份宫保鸡丁"）→ 调 add_to_cart，dish_name 用用户原话
- 用户问有什么菜/看菜单 → 调 get_menu
- 用户说好了/下单/结算 → 先调 get_cart 展示购物车，再调 get_addresses 让用户选地址
- 用户确认下单 → 调 submit_order

只有以下情况用文字回复，不调工具：
- 用户问营业时间、退款政策等非点餐问题
- 用户要求转人工

示例：
用户：帮我点2份宫保鸡丁 → 你必须调 add_to_cart(dish_name="宫保鸡丁", quantity=2)
用户：有什么菜 → 你必须调 get_menu()
用户：好了下单 → 你必须调 get_cart() 展示购物车，然后调 get_addresses()

回复简短友好。"""


def call_llm(messages):
    """调用 DeepSeek，返回 chat completion 响应。"""
    client = OpenAI(api_key=config.DEEPSEEK_API_KEY, base_url=config.LLM_BASE_URL)
    resp = client.chat.completions.create(
        model=config.LLM_MODEL,
        messages=messages,
        tools=TOOLS,
        tool_choice="auto",
        temperature=0.3,
    )
    return resp
