# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

SheepAIMall — Spring Boot 3.3.6 / Java 17 / Maven 智能电商商品服务。基础包 `com.xs.sheepaimall`。

## 常用命令

```bash
./mvnw compile              # 编译
./mvnw test                 # 运行全部测试
./mvnw test -Dtest=XXX      # 运行单个测试类
./mvnv spring-boot:run      # 启动应用（端口 8080，MySQL/Redis/RabbitMQ/ES 需本地启动）
./mvnw package              # 打包
```

## 技术栈

| 组件 | 用途 |
|------|------|
| Spring Boot 3.3.6 | 基础框架 |
| MyBatis-Plus 3.5.12 | ORM + 分页（雪花ID主键，全局 ASSIGN_ID） |
| MySQL | 数据库 (sheep_ai_mall) |
| Redis | 缓存 + JWT黑名单 + 库存锁定追踪 + 幂等去重 |
| RabbitMQ | 库存锁定/释放异步消息 + DLX死信重试 |
| Elasticsearch | 商品全文检索 |
| Spring AI (DeepSeek) | AI 自动生成商品文案 |
| Knife4j 4.5.0 | API 文档 (http://localhost:8080/swagger-ui.html) |
| 微信支付 SDK | JSAPI 支付 |
| Hutool 5.8.37 | 通用工具集 |
| Aliyun OSS SDK | 图片文件存储（头像/商品图/资质图） |
| jjwt 0.12.6 | JWT Token 签发与验证 |
| Spring Security Crypto | 仅密码 BCrypt 加密（不含安全框架） |

## 配置体系

- `application.yml` — 标准配置，值引用 `${sheep.*}` 或 `${sky.alioss.*}` 占位符
- `application-dev.yml` — 开发环境实际值，定义所有 `sheep.*` 和 `alioss.*` 属性

### 关键配置项

| 配置 | 位置 | 说明 |
|------|------|------|
| `alioss.endpoint/access-key-id/...` | application-dev.yml | 阿里云 OSS，用于图片文件存储 |
| `sheep.jwt.secret/expiration` | application-dev.yml | JWT 签名密钥（256位）和过期时间（默认2h） |
| `sheep.mall.order.pay-timeout-minutes` | application-dev.yml | 支付超时分钟数，默认15 |
| `wechat.pay.enabled` | application.yml | 微信支付开关，默认 false |
| `spring.ai.openai.*` | application.yml | 实际对接 DeepSeek API |

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
├── util/           → 工具类（OssUtil 阿里云 OSS 上传）
├── common/         → R, ResultCode, BizException, CacheHelper, RabbitMQConstants
├── config/         → 各类 @Configuration（RabbitMQ、AliOssProperties、OssConfiguration、DataInitRunner 等）
├── consumer/       → RabbitMQ @RabbitListener 消费者
├── scheduler/      → @Scheduled 定时任务
└── repository/     → Spring Data ES Repository
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

### 权限模型

- 前端 GET 浏览接口公开（游客可访问）
- 写操作（创建/修改/删除）需要对应的具体权限如 `spu:create`、`spu:update`
- 权限标识符在 JWT 的 claims 中携带，由 `AuthInterceptor` 校验
- 用户拥有的权限由 `sys_user_role` → `sys_role_permission` → `sys_permission.perm_code` 链路决定

### 角色体系（schema-rbac.sql）

| 角色 | ID | 编码 | 说明 |
|------|------|------|------|
| 超级管理员 | 1 | ROLE_ADMIN | 所有权限 |
| 运营人员 | 2 | ROLE_OPERATOR | 商品+订单管理 |
| 只读用户 | 3 | ROLE_VIEWER | 仅查看 |
| 商家 | 4 | ROLE_MERCHANT | 商家后台权限 |

商家拥有 `merchant:*` 权限（info:update, goods:manage, order:manage, stat:view, review:view），**不拥有** `spu:*` 或 `order:*`，不能绕过 MerchantController 直接操作 SpuController。

## 商家系统

三种视角隔离：

| 视角 | 控制器 | 说明 |
|------|--------|------|
| 买家端 | MerchantController GET /api/merchant/{id} | 浏览商家列表/详情/商品 |
| 商家后台 | MerchantController /api/merchant/info, goods, order... | 需 @RequirePermission("merchant:*") |
| 平台管理 | AdminMerchantController /api/admin/merchant/... | 需 @RequirePermission("merchant:audit/list") |

**Cashier 模式**：商家后台所有查询通过 `getCurrentMerchant()` 从 JWT → UserContext → Merchant 表获取当前商家 ID，SQL 全部 `WHERE merchant_id = ?` 硬隔离，无法操作其他店铺数据。

```
用户登录 → JWT含userId → getCurrentMerchant()查merchant表 → merchant.id
  → 商品列表: WHERE merchant_id = merchant.id
  → 订单列表: 通过SPU→order_item→order_info链路过滤
```

### DataInitRunner 测试账号

| 账号 | 密码 | 角色 | 商家ID |
|------|------|------|--------|
| admin | 123456 | ROLE_ADMIN | — |
| zhangsan | 123456 | ROLE_VIEWER + ROLE_MERCHANT | 1001 |
| lisi | 123456 | ROLE_VIEWER + ROLE_MERCHANT | 1002 |

## 统一文件上传

```
POST /api/upload/image?type=avatar|goods|cert
  → MultipartFile(file) 或 base64(avatarUrl)
  → OssUtil → Aliyun OSS → 返回完整 URL
```

- `avatar` → 头像，路径 `avatar/yyyyMMdd/`
- `goods` → 商品图片，路径 `goods/yyyyMMdd/`
- `cert` → 营业执照，路径 `cert/yyyyMMdd/`

权限控制：avatar 任何登录用户可传，goods/cert 需对应 merchant 权限。
旧头像自动删除：`AuthController.updateAvatar()` 上传新图后调用 `OssUtil.deleteByUrl()` 清理 OSS 旧文件。
商家修改店铺信息时同样会清理旧的 shopLogo 和 businessLicense。

## 库存锁定/释放（RabbitMQ 异步）

见项目代码中的 RabbitMQ 配置和 consumer 包，时序如下：

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
| `spu::detail:{id}` | CacheHelper | SPU 详情 |
| `jwt:blacklist:{md5(token)}` | Token 剩余有效期 | 退出登录黑名单 |

## 关键约定

- **返回**: Controller 统一 `R<T>`，成功 `R.ok(data)`，失败抛 `BizException`
- **异常**: `BizException`(含 ResultCode) → `GlobalExceptionHandler` → `R.fail`
- **分页**: MyBatis-Plus `Page<T>`（MySQL 方言插件已配置）
- **主键**: 雪花算法 `ASSIGN_ID`（全局配置，实体标注 `@TableId`）
- **字段填充**: `createTime` / `updateTime` 由 `MetaObjectHandlerConfig` 自动填充
- **缓存**: `CacheHelper` 封装防穿透(null标记)、防击穿(SETNX分布式锁)、防雪崩(TTL抖动±20%)
- **配置**: 新增配置项在 application-dev.yml 设实际值，application.yml 用占位符引用
- **订单状态**: 0待支付 1已支付 2已发货 3已完成 4已取消（仅 0→1 或 0→4）
