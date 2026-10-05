# 让 Python 在 Windows 控制台正确显示中文
import sys
sys.stdout.reconfigure(encoding="utf-8")
