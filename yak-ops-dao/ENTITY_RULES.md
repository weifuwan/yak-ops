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

- Entity 放在 `io.yak.ops.dao.entity.<capability>` 包。
- 数据库表映射类统一以 `Entity` 结尾。
- 显式使用 `@TableName` 声明真实表名。
- 当前 Yak Ops 使用 `Long` 自增主键，主键字段统一为 `id`，使用 `@TableId(type = IdType.AUTO)`。
- 数据库时间字段统一使用 `LocalDateTime`。
- 普通字段使用 Java camelCase，并依赖 MyBatis-Plus underscore-to-camel 映射。
- 只有特殊映射、自动填充或逻辑删除等真实语义才使用 `@TableField / @TableLogic`。
- 固定状态 / 类型优先使用枚举；枚举 Java 类型与 Flyway 中的真实存储格式必须一致。
- Entity 类必须有中文 JavaDoc，并包含 `@author` 与 `@since`。
- Entity 的持久化业务字段必须有中文 JavaDoc。
- Entity 字段注释与 Flyway 对应字段 COMMENT 的业务语义保持一致。
- 敏感字段必须使用 `@ToString.Exclude` 或等价机制，禁止凭证进入日志。
- 表存在逻辑删除字段时使用 `@TableLogic`，删除语义必须与 Flyway 默认值一致。
- 表存在 MyBatis 自动填充字段时，Entity 必须明确对应的 Fill 规则。
- Schema 变化时必须同步修改 Entity；Entity 变化涉及持久化结构时必须同步检查 Flyway。

## Base Entity

Yak Ops 当前不强制 `BaseEntity`。

原因：
- Security 与 Datasource 现有字段并不完全相同。
- 当前主键与审计字段尚未形成所有表都适用的统一 Contract。

只有当多个真实 Entity 已稳定共享同一组 `id / createTime / updateTime / createBy / updateBy` 语义后，才允许引入 `BaseEntity`。

禁止为了代码看起来整齐提前建立 BaseEntity。

## Generated / Infrastructure Columns

数据库生成列、纯索引辅助列等不需要机械映射为 Entity 字段，前提是：
- 应用不直接读取或写入该列。
- Mapper / Repository 不依赖该列作为业务数据。
- Flyway 中已明确其生成或基础设施用途。

## Must Not

- 使用 PO / DO 作为数据库表映射对象。
- 把 Entity 作为 HTTP 返回对象。
- 让 Controller 直接依赖 Entity。
- 为普通下划线映射重复声明 `@TableField`。
- 在 Entity 内实现业务流程或跨表业务规则。
- 为了复用少量字段提前引入 BaseEntity。
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
