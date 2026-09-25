# Entity Rules

Scope:
- `yak-ops-dao/src/main/java/io/yak/ops/dao/entity/**`

Depends On:
- `/JAVA_RULES.md`
- `/yak-ops-common/COMMON_RULES.md`
- `./DAO_RULES.md`
- `./FLYWAY_RULES.md`

Owns:
- Java 持久化模型
- 数据库表到 Java 对象的映射

## Must

- Entity 放在 `io.yak.ops.dao.entity.<capability>` 包，公共基类允许放在 `io.yak.ops.dao.entity`。
- 数据库表映射类统一以 `Entity` 结尾。
- 所有真实表 Entity 统一继承 `BaseEntity`。
- 公共字段统一为 `id / createTime / updateTime / createBy / updateBy`，业务 Entity 不重复声明。
- 主键统一使用 `String`，由 Common 的雪花 ID 能力生成，禁止数据库自增。
- 新增前调用 `initCreate`；更新前调用 `initUpdate`，调用方有操作人标识时必须传入。
- 数据库时间字段统一使用 `LocalDateTime`。
- 显式使用 `@TableName` 声明真实表名。
- 普通字段使用 Java camelCase，并依赖 MyBatis-Plus underscore-to-camel 映射。
- 只有特殊映射、自动填充或逻辑删除等真实语义才使用 `@TableField / @TableLogic`。
- 固定状态 / 类型优先使用枚举；枚举 Java 类型与 Flyway 中的真实存储格式必须一致。
- 数值持久化枚举使用 Common 中声明 `@EnumValue` 的 `value` 字段自动读写，Entity 不保存裸 `Integer` 状态码，Repository / Business 不手动做枚举与数字转换。
- Entity 类必须有中文 JavaDoc，并包含 `@author` 与 `@since`。
- Entity 的持久化业务字段必须有中文 JavaDoc。
- Entity 字段注释与 Flyway 对应字段 COMMENT 的业务语义保持一致。
- 敏感字段必须使用 `@ToString.Exclude` 或等价机制，禁止凭证进入日志。
- 表存在逻辑删除字段时使用 `@TableLogic`，删除语义必须与 Flyway 默认值一致。
- Schema 变化时必须同步修改 Entity；Entity 变化涉及持久化结构时必须同步检查 Flyway。

## Base Entity

`BaseEntity` 统一承载：
- `String id`
- `LocalDateTime createTime`
- `LocalDateTime updateTime`
- `String createBy`
- `String updateBy`
- `initCreate / initUpdate`

`BaseEntity` 只负责公共持久化字段初始化，不获取 Spring Security 上下文，不承载业务流程。

## Generated / Infrastructure Columns

数据库生成列、纯索引辅助列等不需要机械映射为 Entity 字段，前提是：
- 应用不直接读取或写入该列。
- Mapper / Repository 不依赖该列作为业务数据。
- Flyway 中已明确其生成或基础设施用途。

## Must Not

- 使用 PO / DO 作为数据库表映射对象。
- 把 Entity 作为 HTTP 返回对象。
- 让 Controller 直接依赖 Entity。
- 在业务 Entity 重复声明 BaseEntity 已拥有的公共字段。
- 使用 `Long` 自增主键或在业务代码自建 UUID / ID 方案。
- 为普通下划线映射重复声明 `@TableField`。
- 在 Entity 内实现业务流程或跨表业务规则。
- 使用 `java.util.Date` 表达普通数据库时间字段。
- 在没有 Migration 的情况下改变枚举存储格式。
- 在 `toString` 中暴露密码、Token、连接参数、密钥等敏感数据。

## Boundary

Entity 只镜像持久化语义。

```text
HTTP DTO / VO
     ↑
Business / Service
     ↑
Repository
     ↑
Entity
     ↑
Flyway Schema
```

API 形状属于 DTO / VO；业务行为属于 Business / Service；Entity 不跨越持久化边界。
