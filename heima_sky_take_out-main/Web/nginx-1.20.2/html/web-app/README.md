# 苍穹外卖 · 网页版（个人练习项目）

C 端 H5 单页应用，复刻原微信小程序端的点餐体验，去掉了所有微信相关代码。商家功能不在本目录，统一由**原版 PC 后台**承担（见仓库根 `README.md`，访问 http://localhost/admin/）。

- 无微信登录：账号密码登录（用户角色），支持注册
- 无微信支付：点击支付即模拟付款成功（后端直接改订单状态，含幂等保护）
- 无微信回调 `/notify/paySuccess`，无 `WeChatProperties` / `WeChatPayUtil` / 微信支付依赖
- 强依赖 Redis：购物车、店铺状态、菜品/套餐/报表缓存、下单防重锁、订单号序列、超时延时队列都在 Redis
- 商品图片通过后端上传到阿里云 OSS（也兼容库里已有的图片 URL）

## 目录

```
web-app/
  index.html        入口（手机壳单页应用 + 全局底部导航容器）
  css/style.css     样式
  js/api.js         API 封装（同源走 nginx 代理）
  js/util.js        工具函数
  js/app.js         hash 路由 + 全部页面
  images/           本地图片
```

## 主要页面

- 点餐：分类侧栏 + 菜品卡片 + 购物车 + 结算；打烊时自动置灰加菜/结算
- 订单：状态标签筛选、订单卡片（按状态配色）、支付/取消/再来一单/催单
- 我的：用户信息、订单/地址/点餐统计快捷入口、地址簿管理、退出登录
- 底部主导航（点餐/订单/我的）全局常驻，切页不重建

## 接口约定

- 登录：`POST /user/user/login`，body `{username,password,role:1}`，返回 `token`
- 后续请求头携带 `authentication: <token>`（免鉴权：登录、注册、`GET /user/shop/status`）
- 所有接口字段与行为以后端仓库根目录《接口文档.md》为准

## 启动

由 nginx 托管，无需构建：

1. 初始化数据库（MySQL 3307，root/1234，库名 sky_web）：
   ```bash
   mysql -u root -p1234 -P 3307 --default-character-set=utf8mb4 < sky_web.sql
   ```
2. 启动 Redis（6379）与后端（8080，JDK17 + Maven）：
   ```bash
   cd heima_sky_take_out-main
   mvn package -DskipTests
   java -jar sky-server/target/sky-server-1.0-SNAPSHOT.jar
   ```
3. 启动 `Web/nginx-1.20.2/nginx.exe`，浏览器打开 http://localhost/

> 直接用浏览器打开 `index.html`（file 协议）时，需把 `js/api.js` 的 `API_BASE` 改为 `http://localhost:8080`；正常使用请走 nginx。

## 演示账号（密码均为 123456）

| 角色 | 入口 | 用户名 |
| --- | --- | --- |
| 用户 | http://localhost/ | zhangsan / lisi |
| 商家（PC 后台） | http://localhost/admin/ | admin |

## nginx 配置

`Web/nginx-1.20.2/conf/nginx.conf`：

- `/` 托管本 H5（HTML `no-cache`、css/js 5 分钟、图片字体 7 天、gzip）
- `/user/**` 反向代理到 `http://localhost:8080/user/`
- `/api/**` 反向代理到 `http://localhost:8080/admin/`（PC 后台使用）
- `/admin/` 托管原版 PC 后台（`html/sky`）
- `/ws/**` 代理后端 WebSocket（来单/催单提醒）
