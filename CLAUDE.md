# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

SheepAIMall — Spring Boot 3.4.1 / Java 17 / Maven 智能电商商品服务。基础包 `com.xs.sheepaimall`。

## 常用命令

```bash
./mvnw compile              # 编译
./mvnw test                 # 运行全部测试
./mvnw test -Dtest=XXX      # 运行单个测试类
./mvnw spring-boot:run      # 启动应用
./mvnw package              # 打包
```

## 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.4.1 | 基础框架 |
| MyBatis-Plus | 3.5.12 | ORM + 分页 |
| MySQL | - | 数据库 (connector-j, runtime) |
| Knife4j | 4.5.0 | API 文档 (Swagger 增强) |
| Hutool | 5.8.37 | 通用工具集 |
| Lombok | - | 编译期代码简化 |

## 包结构 & 分层职责

```
com.xs.sheepaimall
├── controller/     → 接口层，接收请求、参数校验、调用 service
├── service/        → 业务接口
│   └── impl/       → 业务实现
├── mapper/         → MyBatis-Plus Mapper 接口（已通过 @MapperScan 自动扫描）
├── entity/         → 数据库实体类
├── common/         → 通用组件
│   ├── R<T>          统一返回结果封装
│   ├── ResultCode    状态码枚举
│   └── BizException  业务异常（由 GlobalExceptionHandler 统一拦截）
└── config/         → 配置类
    ├── GlobalExceptionHandler  全局异常处理
    ├── CorsConfig              跨域配置
    ├── MyBatisPlusConfig       分页插件 + MapperScan
    └── Knife4jConfig           API 文档配置
```

## 关键约定

- **返回类型**: 所有 Controller 方法统一返回 `R<T>`，不走裸数据
- **异常处理**: 业务异常抛 `BizException`，由 `GlobalExceptionHandler` 转为 `R.fail`
- **分页**: 使用 MyBatis-Plus `Page<T>`，分页插件已配置 MySQL 方言
- **主键策略**: 雪花算法 `ASSIGN_ID`（在 `application.yml` 中配置）
- **逻辑删除**: 字段名 `deleted`，0=未删除 1=已删除
- **数据库**: 连接 `sheep_ai_mall` 库，配置在 `application.yml`
