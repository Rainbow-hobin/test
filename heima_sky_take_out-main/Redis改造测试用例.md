# 苍穹外卖网页版 · Redis 改造测试用例与报告

- 测试日期：2026-09-26
- 测试对象：本次 Redis 改造涉及的 5 个功能点
- 测试方式：HTTP 接口实测（经 nginx :80 或直连后端 :8080）+ Redis（redis-cli :6379）/ MySQL（:3307）状态核对
- 用例设计方法：正常场景 + 边界值 + 异常值 + 安全测试（SQL 注入）+ 原有功能回归
- 测试结果：**47 个用例全部通过（47/47）**
- **2026-09-27 追加第三批"缓存穿透 / 击穿 / 雪崩 / 故障降级 / 主动失效"测试：全量 19 项 + 计时与陈旧值补充复测 11 项，全部通过（见第九节）**

---

## 一、测试环境

| 项 | 值 |
| --- | --- |
| 后端 | Spring Boot 2.7.3，JDK 17，端口 8080（改造后重新打包重启） |
| 前端代理 | nginx 1.20.2，端口 80，`/user/**`、`/merchant/**` 反代 8080 |
| 数据库 | MySQL 8.4，端口 3307，库 `sky_web` |
| 缓存 | Redis（Windows 版），端口 6379 |
| 测试账号 | 用户 `zhangsan`（id=1）、`lisi`（id=2）；管理员 `admin`，密码均为 123456 |
| 测试商品 | 菜品 #1 宫保鸡丁 ¥28（含口味）、#2 鱼香肉丝 ¥26；套餐 #1 单人商务餐 ¥35；地址簿 #1 |

## 二、改造范围与对应测试模块

| 模块 | 改造点 | 涉及代码 |
| --- | --- | --- |
| A 登录限流 | 连续失败 5 次锁 10 分钟，成功即清零 | UserServiceImpl |
| B 购物车 | MySQL 购物车表 → Redis Hash（`cart:{userId}`） | ShoppingCartServiceImpl、OrderServiceImpl |
| C 下单 | Redis INCR 订单号 + 5 秒防重锁 | OrderServiceImpl |
| D 再来一单 | 订单明细回填 Redis 购物车，同款数量累加 | OrderServiceImpl、ShoppingCartService |
| E 报表缓存 | 4 个统计接口 `@Cacheable`，TTL 30 分钟 | ReportServiceImpl、RedisConfiguration |
| F 回归 | 店铺状态、C 端菜品缓存等原有 Redis 功能 | ShopController、user/DishController |

## 三、用例汇总

| 模块 | 用例数 | 通过 | 失败 |
| --- | --- | --- | --- |
| A 登录限流（含安全） | 11 | 11 | 0 |
| B 购物车 | 12 | 12 | 0 |
| C 下单 | 14 | 14 | 0 |
| D 再来一单 | 3 | 3 | 0 |
| E 报表缓存 | 4 | 4 | 0 |
| F 原有功能回归 | 3 | 3 | 0 |
| **合计** | **47** | **47** | **0** |

> 编号带字母后缀（如 01b、32b、36a/36b）为同一业务点的状态断言或多次操作子用例。第一批（A+B）23 个、第二批（C+D+E+F）24 个，分两轮实跑，结果均为全 PASS。

---

## 四、详细测试用例

### 模块 A：登录失败限流

| 编号 | 用例 | 前置/步骤 | 预期结果 | 实测 |
| --- | --- | --- | --- | --- |
| TC-01 | 正确密码正常登录 | `zhangsan/123456` 登录 | code=1，返回 JWT | PASS |
| TC-01b | 登录成功清除计数 | 登录后查 `user:login:fail:zhangsan` | key 不存在 | PASS |
| TC-02 | 错误密码被拒 | 密码 `wrongpwd` | code=0，提示密码错误 | PASS |
| TC-02b | 失败计数累加 | 查失败计数 key | 值 = 1，且带 10 分钟 TTL | PASS |
| TC-04 | **边界值**：第 4 次失败 | lisi 连续错 4 次 | 计数值 = 4（未到 5 次不锁定） | PASS |
| TC-04b | **边界值**：4 次失败后第 5 次输入正确密码 | 立即用正确密码登录 | code=1，登录成功（5 次才锁，4 次放行） | PASS |
| TC-04c | 成功登录后计数清零 | 查计数 key | key 不存在 | PASS |
| TC-03 | 连续失败 5 次后锁定 | lisi 连错 5 次后，第 6 次用**正确密码** | code=0，提示"登录失败次数过多，请10分钟后再试" | PASS |
| TC-03b | 锁定时计数保持 | 查计数 key | 值 = 5 | PASS |
| TC-05 | **异常值**：空密码 | `password:""` | code=0，登录失败 | PASS |
| TC-06 | **安全测试**：SQL 注入 | 用户名填 `' OR '1'='1` | code=0，账号不存在，未返回 token（MyBatis #{} 参数化，注入无效） | PASS |

### 模块 B：购物车（Redis Hash，key=`cart:{userId}`）

| 编号 | 用例 | 步骤 | 预期结果 | 实测 |
| --- | --- | --- | --- | --- |
| TC-10 | 空购物车查看 | 清空后 list | code=1，返回空数组 | PASS |
| TC-11 | 添加菜品（带口味） | add 宫保鸡丁/微辣 | number=1，name/image/amount(=28.00) 快照完整 | PASS |
| TC-12 | 同款同口味数量累加 | 再次 add 相同商品 | number=2（Hash field 复用，不新增） | PASS |
| TC-13 | 同款不同口味独立 | add 宫保鸡丁/中辣 | 购物车出现 2 个宫保鸡丁条目 | PASS |
| TC-14 | 添加套餐 | add 套餐 #1 | number=1，amount=35.00 | PASS |
| TC-15 | 购物车条目总数 | list | 共 3 个不同条目 | PASS |
| TC-16 | 减品（数量>1） | 宫保鸡丁/微辣 sub 一次 | number 由 2 变 1 | PASS |
| TC-17 | **边界值**：减到 0 | number=1 时再 sub | field `dish:1:weila` 从 Hash 删除 | PASS |
| TC-18 | 对不存在的商品减品 | 再次 sub 已删除项 | code=1，幂等不报错，其他条目不受影响 | PASS |
| TC-19 | 用户数据隔离 | 用 lisi 查看购物车 | 空，看不到 zhangsan 的商品 | PASS |
| TC-20 | **异常值**：空商品体 | add `{}`（菜品/套餐 id 均不传） | 不返回成功（HTTP 500，见第六节"已知遗留问题 1"） | PASS（按预期被拒绝，未产生脏数据） |
| TC-21 | 清空购物车 | delete | Redis key `cart:1` 被删除 | PASS |

### 模块 C：下单（订单号 + 防重锁 + 购物车联动）

| 编号 | 用例 | 步骤 | 预期结果 | 实测 |
| --- | --- | --- | --- | --- |
| TC-30 | **异常值**：空购物车下单 | 购物车为空提交 | code=0，"购物车数据为空，不能下单" | PASS |
| TC-31 | **异常值**：地址不存在 | addressBookId=999999 | code=0，"用户地址为空，不能下单" | PASS |
| TC-31b | 无效提交不加锁 | TC-31 后查 `order:submit:lock:1` | key 不存在（本次测试中修复，见第五节） | PASS |
| TC-32 | 正常下单 | 2 件商品 + 合法地址 | code=1，返回订单 id、金额、下单时间 | PASS |
| TC-32b | 订单号格式 | 检查 orderNumber | 20 位纯数字：`yyyyMMddHHmmss` + 6 位序列 | PASS |
| TC-32c | 当日序列原子自增 | 比对下单前后 `order:seq:20260926` | 恰好 +1（实测 1→2） | PASS |
| TC-33 | 防重复提交 | 立即再次提交同一订单 | code=0，"您操作太快了，请勿重复提交" | PASS |
| TC-33b | 防重锁存在且带 TTL | 查锁 key | 存在，TTL 在 1~5 秒之间 | PASS |
| TC-34a | 下单后清空购物车 | 查 `cart:1` | key 已删除 | PASS |
| TC-34b | 订单明细落库 | 查 order_detail 表 | 生成 2 条明细（菜品+套餐） | PASS |
| TC-35 | 锁过期后可再次下单 | 等待 6 秒后重新加购并下单 | code=1（第二单实测号 `20260926234830000003`） | PASS |
| TC-35b | 订单号全局不重复 | 比较两单号码 | 两单号码不同（...000002 vs ...000003） | PASS |
| TC-36a | 用户取消订单（A 单） | PUT `/user/order/cancel/{idA}` | code=1，订单状态变为 6 已取消 | PASS |
| TC-36b | 用户取消订单（B 单） | PUT `/user/order/cancel/{idB}` | code=1，订单状态变为 6 已取消（DB 实测 #30/#31/#32 均为 6） | PASS |

### 模块 D：再来一单

| 编号 | 用例 | 步骤 | 预期结果 | 实测 |
| --- | --- | --- | --- | --- |
| TC-40 | 明细回填购物车 | 对订单执行 repetition | code=1，购物车出现原订单 2 件商品 | PASS |
| TC-40b | 回填条目数 | list | 恰好 2 条 | PASS |
| TC-41 | 再次"再来一单"数量累加 | 连续执行 2 次 | 两个条目 number 均为 2（Hash 复用、不产生重复条目） | PASS |

### 模块 E：报表统计缓存

| 编号 | 用例 | 步骤 | 预期结果 | 实测 |
| --- | --- | --- | --- | --- |
| TC-50 | 四个统计接口可用 | 管理员登录后依次调用营业额/用户/订单/Top10 | 全部 code=1 | PASS |
| TC-51 | 缓存写入 Redis | 查 `reportCache::*` | 出现 4 个 key（turnover/user/order/top10，含日期区间后缀） | PASS |
| TC-51b | TTL 为 30 分钟 | 逐个 TTL | 均在 1700~1800 秒之间 | PASS |
| TC-52 | 命中缓存结果一致 | 同参数再调一次营业额接口 | dateList、turnoverList 与首次完全一致 | PASS |

### 模块 F：原有 Redis 功能回归

| 编号 | 用例 | 步骤 | 预期结果 | 实测 |
| --- | --- | --- | --- | --- |
| TC-60 | 店铺营业状态 | GET `/user/shop/status` | code=1，返回营业状态（SHOP_STATUS 读写正常） | PASS |
| TC-61a | C 端菜品列表接口 | 删除 `dish_1` 后请求 `/user/dish/list?categoryId=1` | code=1 且返回菜品数据 | PASS |
| TC-61b | 菜品列表缓存写入 | 请求后查 Redis | `dish_1` 被重新写入 | PASS |

---

## 五、测试中发现并修复的问题

### 已修复：防重锁位置导致"合法重试被误伤"

- 现象：首版实现中，下单接口一进入就获取防重锁，**先于地址/购物车校验**。当用户因地址不存在等原因下单失败后，修正地址立刻重试，会被锁拦截 5 秒（TC-31 → TC-32 联测时暴露）。
- 修复：将 `SET NX EX 5` 加锁点移动到**业务校验通过之后、写库之前**（OrderServiceImpl.submitOrder）。无效请求不再产生锁；并发的两个合法请求仍只有一个能成功。
- 回归：TC-31b、TC-32、TC-33 全部通过。

### 测试脚本自身问题（非应用缺陷，已排除）

- PowerShell 5.1 中通过 `if` 表达式给 `-Body` 赋值会把 `byte[]` 包装成 `Object[]`，导致请求体畸形、服务端 400。强转 `[byte[]]` 后正常。曾造成一批"假失败"，均已重跑验证。

## 六、已知遗留问题（改造前就存在，本次未改）

1. **加购空商品体返回 500（TC-20）**：`add` 请求菜品/套餐 id 均不传时，`getById(null)` 返回 null 后取属性触发 NPE。原版代码同样如此，实际前端不会发出该请求。建议后续在入口加参数校验，返回 code=0 业务错误。
2. **下单不传 `packAmount` 返回 500**：实体 `Orders.packAmount` 是基本类型 `int`，DTO 为 `Integer`，BeanUtils 拷贝 null 到基本类型抛 IllegalArgumentException。前端固定传 `packAmount:0` 不会触发，测试已按前端实际报文传参。

## 七、测试数据清理说明

- Redis：`cart:*`、登录失败计数、防重锁等临时 key 均已删除；`order:seq:*`（序列）、`reportCache::*`（30 分钟自动过期）、`dish_*`（业务缓存）属正常数据予以保留。
- MySQL：测试产生的订单 #30、#31、#32 均已调用取消接口，状态为"6 已取消"，订单明细保留（与真实用户取消订单后的状态一致），未直接操作数据库删除。

## 八、测试结论

本次 Redis 改造的 5 个功能点（登录限流、购物车 Hash、INCR 订单号、下单防重、报表缓存）在正常、边界、异常与安全场景下行为均符合预期；购物车与订单（下单/再来一单/取消）联动逻辑正确；原有 Redis 功能（店铺状态、菜品缓存）回归正常。测试中发现的 1 个应用逻辑问题已修复并验证，2 个历史遗留缺陷已登记，建议后续迭代处理。

---

## 九、第三批：缓存穿透 / 击穿 / 雪崩防护改造（2026-09-27）

### 9.1 防护方案

新增统一缓存组件 `com.sky.cache.CacheClient`，所有**业务缓存**（`dish_*`、`setmealCache:*`、`reportCache:*`）的读写均经其核心泛型方法
`getWithProtection(key, ttl, dbLoader)`，以 Cache-Aside 为基础叠加四重防护：

| 缓存问题 | 防护手段 | 实现要点 |
| --- | --- | --- |
| 缓存穿透 | ① 入口参数校验 ② 空值占位符缓存 | Controller 入口拦截 `categoryId` 为 null 或 ≤0 的请求，直接返回空集合、不查库；DB 返回 null 或空集合时写入占位符 `"CACHE_EMPTY"`，TTL 120 秒；命中占位符直接返回空 |
| 缓存击穿 | 互斥重建锁 + 双重检查 | 未命中先 `SET lock:{key} NX EX 10`（owner 为 UUID）；抢锁失败循环等待（20 次 × 50ms）并重读缓存；拿到锁后二次检查再回源；Lua 脚本比对 owner 安全释放，避免误删他人锁 |
| 缓存雪崩 | TTL 随机抖动 + 故障熔断 | 正常 TTL 附加 0~10% 随机抖动（菜品 1h → 3600~3960s，报表 30min → 1800~1980s），避免大面积同时过期 |
| Redis 故障 | 命令快速失败 + 熔断降级 + 陈旧值兜底 | `spring.redis.timeout=1000ms`（原 Lettuce 默认 60s）；任一 Redis 操作异常即打开**5 秒熔断器**，期间跳过全部 Redis 操作直接查 MySQL，熔断到期自动"半开"试探恢复；店铺状态另有 JVM 本地 10s 缓存，Redis 不可用时返回陈旧值，极端情况默认"营业" |

配套改造：

- 读路径：`user/DishController`、`user/SetmealController`（key 改为 `setmealCache:{categoryId}`）、`ReportServiceImpl` 4 个统计方法全部改用 CacheClient，移除 `@Cacheable`；
- 写路径：`admin/DishController`、`admin/SetmealController` 移除 `@CacheEvict`，改为精确删除（单 key）或 `deleteByPattern`；批量删除使用 **SCAN 游标（count 100）** 替代阻塞 Redis 的 `KEYS` 命令；
- 清理：删除 `@EnableCaching` 与旧 `RedisCacheManager`，零新增第三方依赖（未引入 Caffeine/Redisson）。

### 9.2 测试用例与结果（全量 19/19 通过）

测试方式：PowerShell 脚本并发压测（RunspacePool 20 并发）+ redis-cli TTL/KEYS 核对 + 后端日志统计 `DishMapper.list` 回源 SQL 次数 + `Stop-Process redis-server` 真实宕机演练。

| 编号 | 用例 | 步骤 | 预期结果 | 实测 |
| --- | --- | --- | --- | --- |
| CP-1a | 穿透：不存在分类首次请求 | GET dish/list?categoryId=999999 | 返回空数组，DB 回源 1 次 | PASS |
| CP-1b | 空值占位符 | 查 `dish_999999` | 值为 `CACHE_EMPTY`，TTL ≤ 120s | PASS |
| CP-1c | 占位符拦截二次穿透 | 再次请求同参数 | 返回空且后端**0 条回源 SQL** | PASS |
| CP-2a | 非法参数入口拦截 | categoryId 缺失 / 0 / -1 | 直接返回空集合 | PASS |
| CP-2b | 非法参数零副作用 | 查日志与 Redis | 不查 SQL、不写缓存 | PASS |
| CB-1a | 击穿：20 并发可用性 | 删 `dish_1` 后 20 线程同时请求 categoryId=1 | 20 个请求全部成功，各返回 3 条 | PASS |
| CB-1b | 互斥锁防击穿 | 统计日志中 `DishMapper.list` 次数 | **仅 1 次回源 SQL** | PASS |
| CB-1c | 锁安全释放 | 请求结束后查 `lock:dish_1` | 锁 key 已删除 | PASS |
| CB-1d | 重建缓存带抖动 TTL | TTL `dish_1` | 落在 3550~3960s（实测样本均 >3600） | PASS |
| AV-1a | 雪崩：报表 TTL 抖动 | 调 4 个统计接口 × 2 个日期区间共 8 key | 8 个 key TTL 全部在 1800~1980s | PASS |
| AV-1b | 抖动有效非固定 | 比较 8 个 TTL | max > 1830s，各 key 存在差异 | PASS |
| AV-2a | 宕机降级查库 | 杀掉 redis-server 后请求 categoryId=2 | code=1，仍从 MySQL 返回 3 条 | PASS |
| AV-2b | 宕机时店铺状态 | Redis 宕机期间 GET shop/status | code=1，命中 JVM 本地缓存 | PASS |
| AV-2c | Redis 恢复 | 重启 redis-server | PING=PONG | PASS |
| AV-2d | 自动重连+熔断恢复 | 轮询菜品接口直到缓存写回 | Lettuce 自动重连，`dish_1` 重新写入 | PASS |
| EV-0 | 套餐缓存正常 | 预热 setmealCache:5 | C 端返回 2 个套餐 | PASS |
| EV-1a | 真实+占位两类缓存并存 | 再请求 categoryId=999999 | Redis 中 2 个 key（真实值 + `CACHE_EMPTY`） | PASS |
| EV-1b | 管理端停用触发 SCAN 失效 | POST admin/setmeal/status/0?id=1 | `setmealCache*` 全部删除（含占位符，0 个残留） | PASS |
| EV-1c | 恢复启用可见性 | POST status/1 后 C 端查询 | 套餐重新可见（≥1 条） | PASS |

### 9.3 计时与陈旧值补充复测（11/11 通过）

第一轮全量测试中，Redis 宕机用例曾因 **Lettuce 默认命令超时 60 秒**导致请求挂起（后端日志证实约 60s 重连后请求才执行，功能本身正确）。修复（1000ms 超时 + 5s 熔断器 + 陈旧值兜底）后补充计时复测：

| 编号 | 用例 | 预期 | 实测 |
| --- | --- | --- | --- |
| AV2-1a | 宕机瞬间查询店铺状态（本地缓存新鲜） | 200 且 <500ms | PASS（毫秒级） |
| AV2-1b | 宕机时菜品接口（熔断降级直查 MySQL） | 200 返回 3 条且 <3s | PASS（首个失败命令 1s 超时即触发熔断，后续 Redis 操作全部跳过） |
| AV2-1c | 等待本地 TTL 过期（11s）后再查店铺状态 | 200 且 <5s，返回陈旧值 1 | PASS |
| AV2-2a/b | Redis 重启 + 后端熔断半开恢复 | PING 恢复且 `dish_1` 成功写回 | PASS |
| EV-1a/b/c | SCAN 失效链路复核 | 停用前 2 key，停用后 0 残留，启用后重新可见 | PASS |
| RG-1/2/3 | 回归：4 个报表接口、TTL 抖动、二次调用结果一致 | 全 code=1；TTL 均在 1800~1980s；结果逐元素相同 | PASS |

### 9.4 测试中发现并修复的问题

1. **Lettuce 默认 60s 命令超时导致降级形同虚设**：Redis 进程被杀后命令排队等待重连而非立即抛异常，请求被挂起。修复：`application.yml` 增加 `spring.redis.timeout: 1000ms`，使 CacheClient 的 catch 降级逻辑可在 1s 内触发。
2. **降级路径上 5 个 Redis 调用各等 1s（共约 5.1s）**：读、抢锁、双重检查、回填、释放锁均独立超时。修复：CacheClient 内置**故障熔断器**（任一操作失败后 5 秒内跳过全部 Redis 操作），降级请求实际耗时降到 ~1s 量级，同时大幅减轻故障期 Redis 重连压力。
3. **套餐缓存在 Redis 刚重启的连接抖动窗口写入瞬时失败**：属恢复窗口环境抖动（日志为 1 秒命令超时），熔断器半开机制保证下一轮自动恢复；测试脚本相应改为"轮询到缓存真正写回"后再进入后续断言，消除假失败。

### 9.5 数据清理

测试结束后：`dish_999999`、`setmealCache:999999` 等占位符与 `setmealCache:*`、`dish_1/2` 测试缓存均已删除；套餐 #1 已恢复启用（status=1，category_id=5，共 2 个套餐）；店铺状态恢复为"营业"；未产生任何 MySQL 脏数据。
