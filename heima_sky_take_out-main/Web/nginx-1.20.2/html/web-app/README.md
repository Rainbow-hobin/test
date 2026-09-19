# 苍穹外卖 · 网页版（个人练习项目）

复刻微信小程序端（`WeiXinApp`）的网页版，去掉了所有微信相关代码：

- 无微信登录：改为账号密码登录，登录页可选择 **用户 / 商家**，并支持注册
- 无微信支付：点击支付即模拟付款成功（后端直接改订单状态）
- 无微信回调 `/notify/paySuccess`，无 `WeChatProperties` / `WeChatPayUtil` / 微信支付依赖
- 不依赖 Redis（店铺状态、菜品缓存改为内存），不依赖外网图片（菜品图为本地 SVG 占位图）

## 目录

```
web-app/
  index.html        入口（手机壳单页应用）
  css/style.css     样式
  js/api.js         API 封装（同源走 nginx 代理）
  js/util.js        工具函数
  js/app.js         路由 + 全部页面
  images/           本地图片
```

后端改动（`heima_sky_take_out-main`）：

- 用户表 `user` 去掉 `openid`，改为 `username` + `password`(MD5) + `role`(1用户/2商家)
- 新增 `POST /user/user/register` 注册接口
- `POST /user/user/login` 改为账号密码登录，返回 role
- 新增 `PUT /user/order/cancel/{id}` 用户取消订单（模拟退款）
- 新增 `/merchant/**` 商家端接口：订单分页、统计、接单、派送、完成
- 删除 `PayNotifyController`、`WeChatPayUtil`、`WeChatProperties`、微信支付依赖
- `application-dev.yml` 数据库指向 `sky_web`（新库）

## 启动步骤

1. 初始化数据库（MySQL 3307，root/1234）：

   ```bash
   mysql -u root -p1234 -P 3307 --default-character-set=utf8mb4 < sky_web.sql
   ```

2. 启动后端（8080 端口，依赖 JDK17 + Maven）：

   ```bash
   cd heima_sky_take_out-main
   mvn package -DskipTests
   java -jar sky-server/target/sky-server-1.0-SNAPSHOT.jar
   ```

3. 启动前端（nginx 已配置好，见下方）或直接打开 `web-app/index.html`
   （直接打开时需把 `js/api.js` 里的 `API_BASE` 改为 `http://localhost:8080`）

## 演示账号（密码均为 123456）

| 角色 | 用户名 |
| --- | --- |
| 用户 | zhangsan / lisi |
| 商家 | merchant01 / merchant02 |

## nginx 配置

`Web/nginx-1.20.2/conf/nginx.conf` 已更新：

- `http://localhost/` 直接打开网页版
- `/user/**`、`/merchant/**` 反向代理到后端 8080
- 微信小程序目录 `WeiXinApp` 与旧的 Web 管理端已删除
