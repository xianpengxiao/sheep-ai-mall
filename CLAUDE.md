# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

SheepAIMall — Spring Boot 3.3.6 / Java 17 / Maven 智能电商后端。基础包 `com.xs.sheepaimall`。

## 常用命令

```bash
./mvnw compile              # 编译
./mvnw test                 # 运行全部测试
./mvnw test -Dtest=XXX      # 运行单个测试类
./mvnw spring-boot:run      # 启动应用（端口 8080，MySQL/Redis/RabbitMQ/ES 需本地启动）
./mvnw package -DskipTests  # 打包（跳过测试）
```

## 技术栈

| 组件 | 用途 |
|------|------|
| Spring Boot 3.3.6 | 基础框架 |
| MyBatis-Plus 3.5.12 | ORM + 分页（雪花ID主键，全局 ASSIGN_ID） |
| MySQL | 数据库 (sheep_ai_mall) |
| Redis | 缓存 + JWT黑名单 + 库存锁定追踪 + 幂等去重 + 分布式锁 |
| RabbitMQ | 库存锁定/释放异步消息 + DLX死信重试 |
| Elasticsearch 7.17 | 商品全文检索（IK 分词器） |
| Spring AI (DeepSeek) | AI 自动生成商品文案 |
| Knife4j 4.5.0 | API 文档 (http://localhost:8080/swagger-ui.html) |
| 微信支付 SDK v3 | JSAPI 支付 |
| Hutool 5.8.37 | 通用工具集 |
| Aliyun OSS SDK | 图片文件存储（头像/商品图/资质图） |
| jjwt 0.12.6 | JWT Token 签发与验证 |
| Spring Security Crypto | 仅密码 BCrypt 加密（不含安全框架） |
| JavaMail | 邮箱验证码发送 |
| 腾讯云短信 SDK | 短信验证码 |

## 配置体系

### Profile 结构

- `application.yml` — 标准配置，值引用 `${sheep.*}` 占位符
- `application-dev.yml` — 开发环境：localhost 地址，详细 SQL 日志
- `application-docker.yml` — Docker 生产环境：容器名连接，关闭 SQL 日志

### 关键配置项

| 配置 | 说明 | 备注 |
|------|------|------|
| `sheep.datasource.*` | MySQL 连接信息 | host/port/database/username/password |
| `sheep.redis.*` | Redis 连接（含连接池） | database=1 |
| `sheep.rabbitmq.*` | RabbitMQ 连接 | virtual-host=/ |
| `sheep.elasticsearch.uris` | ES 地址 | 空值时注解 `@ConditionalOnExpression` 跳过加载 |
| `sheep.jwt.secret/expiration` | JWT 签名密钥（256位）和过期时间 | dev=7200s, docker=43200s |
| `sheep.mall.order.pay-timeout-minutes` | 支付超时分钟数 | 默认15 |
| `sheep.alioss.*` | 阿里云 OSS | 图片文件存储 |
| `sheep.mail.*` | SMTP 邮箱 | QQ邮箱 587端口 |
| `sheep.tencent.sms.*` | 腾讯云短信 | 空值时仅日志不真实发送 |
| `wechat.pay.enabled` | 微信支付开关 | 默认 false |
| `spring.ai.openai.*` | 实际对接 DeepSeek API | base-url=https://api.deepseek.com |

> **注意**：`@ConditionalOnProperty(name = "spring.elasticsearch.uris")` 在空字符串时仍会匹配（空值不等于 "false"），必须使用 `@ConditionalOnExpression("'${spring.elasticsearch.uris:}' != ''")` 替代才能正确跳过。

## 包结构

```
com.xs.sheepaimall
├── controller/     → 接口层，统一返回 R<T>
├── service/impl/   → 业务逻辑
├── mapper/         → MyBatis-Plus BaseMapper
├── entity/         → 数据库实体（@TableName + @TableId ASSIGN_ID）
├── dto/            → 请求 DTO
├── vo/             → 响应 VO
├── security/       → JWT 认证 (AuthInterceptor) + 权限 (RequirePermission) + UserContext(ThreadLocal)
├── util/           → 工具类（OssUtil、SensitiveWordUtil）
├── common/         → R, ResultCode, BizException, CacheHelper, RabbitMQConstants, CacheConstants
├── config/         → @Configuration（RabbitMQ、AliOssProperties、OssConfiguration、DataInitRunner、ElasticsearchConfig、WebConfig）
├── consumer/       → RabbitMQ @RabbitListener 消费者（库存锁定/释放）
├── scheduler/      → @Scheduled 定时任务（DSR日结/过期、订单自动完成、支付超时取消）
└── repository/     → Spring Data ES Repository（SpuDocument / MerchantDocument）
```

## 安全与权限（RBAC）

### JWT 认证流程

```
请求 → AuthInterceptor.preHandle
  ├─ 白名单路径（登录/注册/公开GET）→ 放行
  ├─ 解析 Authorization: Bearer <token>
  ├─ 校验 JWT → Redis黑名单检查
  ├─ 存入 UserContext (ThreadLocal)：userId, username, permissions, token
  └─ 检查 @RequirePermission 注解 → 校验权限
请求结束 → afterCompletion → UserContext.clear()
```

### WebConfig 白名单

见 `config/WebConfig.java:excludePathPatterns(...)`，所有公开 GET 接口和认证接口都配在这里。新增公开接口时**必须同步添加**，否则返回 401。

### 权限模型

- 前端 GET 浏览接口公开（游客可访问）
- 写操作需要对应权限如 `spu:create`、`spu:update`
- 权限由 `sys_user_role` → `sys_role_permission` → `sys_permission.perm_code` 链路决定

### 角色体系（schema-rbac.sql）

| 角色 | ID | 编码 | 说明 |
|------|------|------|------|
| 超级管理员 | 1 | ROLE_ADMIN | 所有权限 |
| 运营人员 | 2 | ROLE_OPERATOR | 商品+订单管理 |
| 只读用户 | 3 | ROLE_VIEWER | 仅查看 |
| 商家 | 4 | ROLE_MERCHANT | 商家后台权限 |

商家拥有 `merchant:*` 权限（info:update, goods:manage, order:manage, stat:view, review:view），**不拥有** `spu:*` 或 `order:*`。

### DataInitRunner 测试账号

| 账号 | 密码 | 角色 | 商家ID |
|------|------|------|--------|
| admin | 123456 | ROLE_ADMIN | — |
| zhangsan | 123456 | ROLE_VIEWER + ROLE_MERCHANT | 1001 |
| lisi | 123456 | ROLE_VIEWER + ROLE_MERCHANT | 1002 |

## 商家系统 — 三种视角隔离

| 视角 | 控制器 | 说明 |
|------|--------|------|
| 买家端 | MerchantController GET /api/merchant/{id} | 浏览商家列表/详情/商品 |
| 商家后台 | MerchantController /api/merchant/info, goods, order... | 需 @RequirePermission("merchant:*") |
| 平台管理 | AdminMerchantController /api/admin/merchant/... | 需 @RequirePermission("merchant:audit/list") |

**Cashier 模式**：商家后台所有查询通过 `getCurrentMerchant()` 从 JWT → UserContext → Merchant 表获取当前商家 ID，SQL 全部 `WHERE merchant_id = ?` 硬隔离。

## 统一文件上传

```
POST /api/upload/image?type=avatar|goods|cert
  → MultipartFile(file) 或 base64(avatarUrl)
  → OssUtil → Aliyun OSS → 返回完整 URL
```

- `avatar` → 头像，路径 `avatar/yyyyMMdd/`，任意登录用户可传
- `goods` → 商品图片，路径 `goods/yyyyMMdd/`，需 merchant 权限
- `cert` → 营业执照，路径 `cert/yyyyMMdd/`，需 merchant 权限
- 旧头像自动删除：上传新图后调用 `OssUtil.deleteByUrl()` 清理 OSS 旧文件

## 库存锁定/释放（RabbitMQ 异步）

```
下单 → MQ stock.lock → 消费者扣库存 → Redis标记 LOCKED
支付 → 删除 LOCKED 标记
取消 → 查 LOCKED → 有则 MQ stock.release → 归还库存
超时(15min) → 定时任务扫描 → 同步归还 + 取消
```

### Redis Key 规范

| Key | TTL | 用途 |
|-----|-----|------|
| `stock:lock:msg:{id}` | 24h | 锁定消息幂等 |
| `stock:release:msg:{id}` | 24h | 释放消息幂等 |
| `stock:locked:{orderId}` | 支付超时×2 | 锁定状态 |
| `cart::{userId}` | — | 购物车缓存 |
| `spu::detail:{id}:v2` | CacheHelper | SPU 详情 |
| `spu::hot_page::{page}::{size}` | CacheHelper | 热门商品分页 |
| `category::tree` | CacheHelper | 分类树 |
| `jwt:blacklist:{md5(token)}` | Token 剩余有效期 | 退出登录黑名单 |
| `lock::cache::{key}` | 短TTL | CacheHelper 分布式锁防击穿 |
| `lock::stock::{spuId}::{skuId}` | 短TTL | 库存操作分布式锁 |

## 资金管理模块

核心表（schema-fund.sql）：

| 表 | 用途 |
|------|------|
| `merchant_settlement_account` | 商家结算账户（含 balance 可提现余额） |
| `commission_config` | 类目佣金规则配置 |
| `order_commission_log` | 订单分佣记录（status: 0待结算 1已结算 2已退款） |
| `merchant_withdraw` | 提现申请（status: 0待审核 1待打款 2已打款 3已驳回） |
| `fund_flow` | 资金流水（flow_type: commission_income/order_income/withdraw_expense 等） |
| `oper_log` | 操作审计日志 |

管理员端：`AdminFundController`（审核提现、查看流水/佣金/统计）
商家端：`MerchantFundController`（绑定账户、申请提现、查看流水/收益/报表）

## DSR 评分体系

- `merchant_dsr` 表每日汇总店铺评分（describe_score, service_score, logistics_score）
- `ProductReview`（product_review 表）扩展了评分字段
- `DsrDailyScheduler` 每日凌晨计算各店铺 DSR
- `DsrExpireScheduler` 定期清理过期评价（默认 90 天不展示）
- `OrderAutoCompleteScheduler` 收货后自动完成订单

## 搜索（Elasticsearch）

- 商品搜索：`ProductSearchServiceImpl` 使用 IK 分词器多字段匹配 + wildcard 单字兜底 + 高亮
- 商家搜索：`MerchantDocument` 搜索店铺名称/简介/经营范围
- ES 条件加载：所有 ES 相关 Bean 通过 `@ConditionalOnExpression("'${spring.elasticsearch.uris:}' != ''")` 控制
- 全量同步 API：`POST /api/search/sync-all`
- IK 插件必须通过 `elasticsearch-plugin install` 安装，手动复制文件会导致 `plugin-descriptor.properties` 缺失

## Docker 部署

部署文件在项目根目录：
- `Dockerfile` — 单阶段构建，基于 eclipse-temurin:17-jre-alpine
- `docker-compose.yml` — 全栈编排（MySQL/Redis/RabbitMQ/ES/Nginx）
- `es-dockerfile` — ES 带 IK 分词器的自定义镜像
- `nginx.conf` — Nginx 反向代理配置
- `application-docker.yml` — Docker 环境配置（容器名连接，关闭 SQL 日志）

> **注意**：ES 必须用 es-dockerfile 构建安装 IK 插件，否则索引创建会报 `mapper_parsing_exception`。
> **注意**：`SHEEP_ELASTICSEARCH_URIS` 设为空字符串不会禁用 ES，需配合代码中的 `@ConditionalOnExpression`。
> **注意**：Dockerfile 未引入 actuator，HEALTHCHECK 应检测 `/` 而非 `/actuator/health`。

## 关键约定

- **返回**: Controller 统一 `R<T>`，成功 `R.ok(data)`，失败抛 `BizException`
- **异常**: `BizException`(含 ResultCode) → `GlobalExceptionHandler` → `R.fail`
- **分页**: MyBatis-Plus `Page<T>`（MySQL 方言插件已配置）
- **主键**: 雪花算法 `ASSIGN_ID`（全局配置，实体标注 `@TableId`）
- **字段填充**: `createTime` / `updateTime` 由 `MetaObjectHandlerConfig` 自动填充
- **缓存**: `CacheHelper` 封装防穿透(null标记)、防击穿(SETNX分布式锁)、防雪崩(TTL抖动±20%)
- **配置**: 新增配置项在 application-dev.yml 设实际值，application.yml 用占位符引用
- **订单状态**: 0待支付 1已支付 2已发货 3已完成 4已取消（仅 0→1 或 0→4）
- **SQL 迁移**: 在 `src/main/resources/sql/` 下创建 `migrate-{描述}-{日期}.sql` 文件，手动执行
- **硬删除**: 所有业务表使用 `deleted` 字段逻辑删除（0=未删 1=已删）
