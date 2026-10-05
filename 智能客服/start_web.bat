@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo 启动智能客服 RAG 测试台...
echo 启动后浏览器打开：http://127.0.0.1:8000
.venv\Scripts\python web_app.py
pause
