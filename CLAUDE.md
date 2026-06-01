# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

SheepAIMall — Spring Boot 3.3.6 / Java 17 / Maven 智能电商商品服务。基础包 `com.xs.sheepaimall`。

## 常用命令

```bash
./mvnw compile              # 编译
./mvnw test                 # 运行全部测试
./mvnw test -Dtest=XXX      # 运行单个测试类
./mvnw spring-boot:run      # 启动应用（端口 8080）
./mvnw package              # 打包
```

## 技术栈

| 组件 | 用途 |
|------|------|
| Spring Boot 3.3.6 | 基础框架 |
| MyBatis-Plus 3.5.12 | ORM + 分页（雪花ID主键） |
| MySQL | 数据库 (sheep_ai_mall) |
| Redis | 缓存 + 库存锁定状态追踪 + 幂等去重 |
| RabbitMQ (spring-boot-starter-amqp) | 库存锁定/释放异步消息 |
| Elasticsearch | 商品全文检索 |
| Spring AI (DeepSeek) | AI 自动生成商品文案 |
| Knife4j 4.5.0 | API 文档 (http://localhost:8080/swagger-ui.html) |
| 微信支付 SDK (wechatpay-java 0.2.12) | JSAPI 支付 |
| Hutool 5.8.37 | 通用工具集 |

## 配置体系

配置文件采用占位符分层模式：

- `application.yml` — Spring Boot 标准配置，值引用 `${sheep.*}` 占位符
- `application-dev.yml` — 开发环境实际值，定义 `sheep.*` 属性（MySQL/Redis/ES/RabbitMQ/DeepSeek API Key/微信支付/订单支付超时）

外部依赖默认均为 `localhost` 标准端口，密码 `123456`（MySQL/Redis）。RabbitMQ 用 `guest/guest`。

### 关键配置项

| 配置 | 位置 | 说明 |
|------|------|------|
| `sheep.mall.order.pay-timeout-minutes` | application-dev.yml | 支付超时分钟数，默认15，超时自动取消 |
| `wechat.pay.enabled` | application.yml | 微信支付开关，默认 false（开发环境不加载微信SDK Bean） |
| `spring.ai.openai.*` | application.yml | 实际对接 DeepSeek API（base-url指向 api.deepseek.com） |
| `spring.rabbitmq.publisher-confirm-type` | application.yml | 生产者确认模式：correlated |
| `spring.rabbitmq.listener.simple.acknowledge-mode` | application.yml | 消费者确认模式：manual |

## 包结构与核心模块

```
com.xs.sheepaimall
├── controller/     → 接口层，统一返回 R<T>
├── service/        → 业务接口
│   └── impl/       → 业务实现（OrderServiceImpl 是最复杂的）
├── mapper/         → MyBatis-Plus BaseMapper（@MapperScan 自动扫描）
├── entity/         → 数据库实体（@TableName + ASSIGN_ID 雪花主键）
├── dto/            → 请求/消息 DTO（含 StockDeductMessage MQ 消息体）
├── vo/             → 响应 VO（含 statusText 等冗余字段）
├── common/         → R、ResultCode、BizException、CacheHelper、RabbitMQConstants
├── config/         → 各类 @Configuration（含 RabbitMQ 队列/交换机定义）
├── consumer/       → RabbitMQ @RabbitListener 消费者
├── scheduler/      → @Scheduled 定时任务
└── repository/     → Spring Data ES Repository
```

## 核心架构：库存锁定/释放（RabbitMQ 异步模式）

这是项目最复杂的子系统，涉及 OrderServiceImpl、StockLockConsumer、OrderTimeoutScheduler 三者协作。

### 流程

```
下单 (OrderService.create)
  └── 同步：校验 → 保存订单+明细(status=0) → 清购物车
  └── 异步：发送 StockDeductMessage → MQ 锁定队列

MQ消费者 (StockLockConsumer.handleStockLock)
  └── Redis SETNX 幂等检查
  └── 检查订单未取消 (status!=4)
  └── 原子扣库存 (WHERE stock>=qty) + 更新销量
  └── Redis 标记 stock:locked:{orderId}=LOCKED (TTL=支付超时×2)

支付成功 (PaymentService / OrderService.updatePayStatus)
  └── 订单 status→1 + 删除 Redis LOCKED 标记

取消订单 (OrderService.cancel)
  └── 查 Redis LOCKED → 已锁定则发 MQ 释放消息 → 消费者归还库存+回退销量
  └── 未锁定则直接取消（锁定消费者会跳过已取消订单）

超时自动取消 (OrderTimeoutScheduler，每30s)
  └── 扫描 status=0 且 create_time < now-15min → 同步归还库存 + status=4
```

### RabbitMQ 拓扑

```
Exchange: sheep.mall.stock.exchange (Topic, durable)
  ├── Queue: sheep.mall.stock.lock.queue    ← routing: stock.lock
  ├── Queue: sheep.mall.stock.release.queue ← routing: stock.release
  └── DLX: sheep.mall.stock.dlx.exchange
        ├── DLQ: sheep.mall.stock.lock.dlq
        └── DLQ: sheep.mall.stock.release.dlq
```

### 可靠性机制

| 机制 | 实现 |
|------|------|
| 生产者确认 | RabbitTemplate ConfirmCallback + ReturnCallback（mandatory=true） |
| 消费者手动 Ack | acknowledge-mode: manual, prefetch: 1 |
| 幂等 | Redis SETNX messageId (TTL 24h)，重复消息直接 Ack |
| 重试（最多3次） | 捕获异常 → 重新 publish 消息（携带 x-retry-count header）→ Ack 旧消息 |
| 死信 | 超最大重试 → basicNack(requeue=false) → DLX → DLQ，订单 remark 标记"需人工处理" |
| 锁定状态追踪 | Redis `stock:locked:{orderId}` = "LOCKED"，供取消/超时判断是否需要归还库存 |
| 防超卖 | 消费者中 `WHERE stock >= quantity SET stock = stock - quantity` 原子更新 |

### Redis Key 规范

| Key 模式 | 用途 | TTL |
|----------|------|-----|
| `stock:lock:msg:{messageId}` | 锁定消息幂等去重 | 24h |
| `stock:release:msg:{messageId}` | 释放消息幂等去重 | 24h |
| `stock:locked:{orderId}` | 库存锁定状态（供取消/超时判断） | pay-timeout-minutes × 2 |
| `cart::{memberId}` | 购物车缓存 | — |
| `spu::detail:{id}` | SPU 详情缓存 | CacheHelper 管理 |

## 订单状态机

| status | 含义 | 触发 |
|--------|------|------|
| 0 | 待支付 | 下单时设置，库存锁定中 |
| 1 | 已支付 | 微信支付回调 / 模拟支付 |
| 2 | 已发货 | （预留） |
| 3 | 已完成 | （预留） |
| 4 | 已取消 | 用户主动取消 / 超时自动取消 |

状态只能从 0→1 或 0→4，不能回退。取消时需先判断库存是否已被 MQ 锁定，已锁定则归还。

## 支付流程

1. `PaymentService.createJsapiPayment(orderId)` — 调微信 JSAPI 下单，保存 PaymentRecord(PENDING)
2. `PaymentService.handleNotify(...)` — 微信回调验签+解密，更新 OrderInfo(status=1, payAmount, payTime) + PaymentRecord(SUCCESS)
3. `PaymentService.mockPay(orderId)` — 开发环境模拟支付，生成 MOCK_ 前缀交易号

微信支付 Bean（JsapiService、RSAAutoCertificateConfig）通过 `@Conditional(wechat.pay.enabled=true)` 控制加载，默认不加载。

## 关键约定

- **返回类型**: Controller 统一返回 `R<T>`，成功用 `R.ok(data)`，失败抛 `BizException`
- **异常处理**: `BizException`（含 ResultCode）由 `GlobalExceptionHandler` 统一转为 `R.fail`
- **分页**: MyBatis-Plus `Page<T>`，已配置 MySQL 方言分页插件
- **主键**: 雪花算法 `ASSIGN_ID`（MyBatis-Plus global-config 配置）
- **字段填充**: `createTime` / `updateTime` 由 `MetaObjectHandlerConfig` 自动填充 `LocalDateTime.now()`
- **逻辑删除**: 部分表保留 `deleted` 字段（TINYINT 0/1），但购物车/订单为直接物理删除
- **配置文件**: 新增配置项遵循 `sheep.*` 命名空间，在 application-dev.yml 设实际值，application.yml 用 `${}` 引用
- **CacheHelper**: 封装了防穿透（null标记）、防击穿（SETNX分布式锁+Lua释放）、防雪崩（TTL抖动±20%）三层保护
