# DTO / VO Rules

Scope:
- `yak-ops-common/src/main/java/io/yak/ops/common/bean/dto/**`
- `yak-ops-common/src/main/java/io/yak/ops/common/bean/vo/**`
- DTO / VO mapping at Controller / Business boundaries

Depends On:
- `/JAVA_RULES.md`
- `/yak-ops-common/COMMON_RULES.md`
- `/yak-ops-dao/ENTITY_RULES.md`

Owns:
- HTTP request DTO contracts
- HTTP response VO contracts
- shared pagination request contract
- shared sorting request contract
- HTTP time-field representation rules

## Design Goals

DTO / VO 是稳定的接口契约，不是 Entity 的别名，也不是为了分层而机械创建的空壳。

Yak Ops 保持四个边界：

```text
HTTP Request
    ↓
DTO
    ↓
Business / Service
    ↓
Repository
    ↓
Entity / DAO Model

Entity / DAO Model
    ↓
Business / Service
    ↓
VO
    ↓
HTTP Response
```

- DTO 只描述输入。
- VO 只描述输出。
- Entity 只描述持久化。
- DAO Model 只描述 DAO 内部特殊查询结果。

禁止让一个对象同时跨越多个边界。

## Package Ownership

```text
bean/dto/common
→ PageQueryDTO / SortDTO 等跨能力输入 Contract

bean/dto/<capability>
→ 能力自己的请求 DTO

bean/vo/<capability>
→ 能力自己的响应 VO
```

Common DTO / VO 不依赖 Mapper、Repository、MyBatis、Entity 或 Spring Web Controller 类型。

## Type Structure

DTO / VO 必须是顶层类型，一个稳定 HTTP Contract 一个文件。

Must:
- DTO / VO 按 capability 和子域组织 package，例如 `vo/datasource/catalog`、`vo/datasource/plugin`。
- 复合响应中的子结构只要拥有独立字段和语义，就拆成独立顶层 VO。
- 公共分页元数据等跨接口响应结构也使用独立顶层类型。

Must Not:
- 在 DTO / VO 内声明 nested class、record、enum 或 interface。
- 使用 `XxxVO.InnerVO` 作为 HTTP / Business Contract。
- 因为某个子结构“只被一个父 VO 使用”就把它定义成内部类。
- 为了减少文件数量牺牲 package ownership 和类型可发现性。

文件数量不是抽象目标。是否创建 VO 取决于它是否拥有稳定 HTTP 响应语义；不应为 Entity、SPI 或内部 Model 机械镜像 VO。

## DTO Contract

DTO 用于 Controller 输入以及 Business / Service 的请求参数。

Must:
- 使用 `DTO` 后缀。
- 输入校验使用 Jakarta Bean Validation，例如 `@NotBlank`、`@Size`、`@Min`、`@Max`。
- 字段使用真实 Java 类型，不为了前端方便把数字、时间、枚举全部降级成 `String`。
- 查询 DTO 需要分页时继承 `PageQueryDTO`。
- 查询时间范围使用显式字段，例如 `createTimeStart / createTimeEnd`。
- 特殊业务动作使用明确名称，例如 `UserPasswordResetDTO`、`DataSourceConnectTestDTO`。

Must Not:
- DTO 继承 Entity。
- DTO 暴露 DAO Model。
- DTO 使用 MyBatis `Page`、`IPage`、`Wrapper` 等基础设施类型。
- 使用 `Map<String, Object> params` 承载正常查询条件。
- 每个业务 DTO 重复声明 `page/pageNum/pageNo/size/limit/pageSize`。
- 为字段完全相同的 Create / Update 请求机械拆两个 DTO。

Naming:
- 普通写入且 Create / Update 合同一致：`XxxDTO`。
- 分页 / 筛选查询：`XxxQueryDTO`。
- 特殊动作：`Xxx<Action>DTO`。
- Create / Update 只有在字段或校验规则真实不同的时候才拆 `XxxCreateDTO / XxxUpdateDTO`。

## VO Contract

VO 用于 HTTP 输出。

Must:
- 使用 `VO` 后缀。
- 只暴露调用方需要的数据。
- 展示字段、派生字段可以放 VO，例如 `environmentName`。
- 时间字段保持 `LocalDate / LocalTime / LocalDateTime` 类型，由统一 JSON 配置负责格式化。
- 敏感字段必须在进入 VO 前完成遮罩、移除或脱敏。

Must Not:
- Controller 直接返回 Entity。
- VO 继承 Entity。
- VO 使用请求校验注解表达输入规则。
- 为了格式化时间把 `LocalDateTime` 转成 `String`。
- 在 VO 中暴露密码、Token、密钥、原始连接凭证等敏感信息。

Naming:
- 默认响应：`XxxVO`。
- 简要响应：`XxxBriefVO`。
- 下拉选项：`XxxOptionVO`。
- 汇总数据：`XxxSummaryVO`。
- 只有真实响应语义不同时才新增新的 VO 类型。

## Time Contract

Java 内部保持类型语义：

```text
LocalDate     → 日期
LocalTime     → 时间
LocalDateTime → 无时区的业务日期时间
```

HTTP JSON 统一格式：

```text
LocalDate     → yyyy-MM-dd
LocalTime     → HH:mm:ss
LocalDateTime → yyyy-MM-dd HH:mm:ss
```

例如：

```json
{
  "createTime": "2026-09-25 13:40:25"
}
```

Must:
- 时间序列化 / 反序列化由 `yak-ops-boot` 的 `HttpJsonConfiguration` 统一拥有。
- DTO 时间输入和 VO 时间输出使用同一格式。
- 特殊外部协议确实要求其他格式时，才允许局部覆盖。

Must Not:
- 在每个 VO 上重复添加相同的 `@JsonFormat`。
- 使用小写 `mm` 表示月份；月份必须使用 `MM`。
- 在 Business 中手动拼接或解析统一格式的时间字符串。

## Pagination Contract

统一分页请求：

```text
PageQueryDTO
├── pageNo
├── pageSize
└── sorts
```

Rules:
- `pageNo` 从 1 开始。
- 默认 `pageNo = 1`。
- 默认 `pageSize = 10`。
- `pageSize` 范围为 1～200。
- 不支持 `pageSize = -1` 或其他“查询全部”约定。
- 查询全部、导出、下拉选项必须使用独立业务接口，不复用分页接口逃逸分页上限。
- 分页查询 DTO 统一继承 `PageQueryDTO`，禁止重新定义分页字段。

Internal / HTTP boundary:

```text
Repository
  ↓
PageData<T>
  ↓
Business / Service
  ↓
PagingData<VO>
  ↓
HTTP
```

DAO / Business 不向上暴露 MyBatis `IPage`。

## Sort Contract

排序请求统一使用：

```json
{
  "sorts": [
    {
      "field": "updateTime",
      "direction": "DESC"
    },
    {
      "field": "name",
      "direction": "ASC"
    }
  ]
}
```

Rules:
- 单次请求最多 3 个排序字段。
- `field` 使用 API camelCase 字段名，例如 `createTime`，不是数据库列名 `create_time`。
- `direction` 只允许 `ASC / DESC`。
- 前端排序字段永远不能直接拼入 SQL。
- Repository 必须为每个查询定义允许排序的字段白名单，并映射到 Entity Lambda 或明确的安全 SQL 表达式。
- 不支持的排序字段必须明确拒绝，不能静默转成任意数据库列。
- 当请求没有排序时，由 Repository 定义业务默认排序。
- 分页排序必须稳定；业务排序字段不唯一时，必须追加唯一键作为最终排序键，例如 `updateTime DESC, id DESC`。

Must Not:
- 使用 `orderByColumn + isAsc` 两组逗号字符串作为新的 Yak Ops Contract。
- 直接调用 `last("ORDER BY " + clientValue)`。
- 将客户端 `field` 原样传给 SQL、Mapper XML 或字符串版 orderBy。

## Mapping Boundary

简单映射优先保持局部：

- Entity → VO 的短小映射优先放在对应 Business / Service 私有方法。
- 字段高度一致且已有安全 Bean copy 能力时可以复用现有工具。
- 没有真实复用价值时，不新增 `Assembler / Converter / Manager`。
- DTO → Entity 不能绕过 Business / Service 的校验、默认值和规范化逻辑。

## Validation Boundary

DTO 负责结构性输入校验：

- 是否为空。
- 长度。
- 数值范围。
- 基础格式。

Business / Service 负责业务语义校验：

- 名称是否重复。
- 状态是否允许迁移。
- 时间开始值是否早于结束值。
- 当前用户是否有权操作。
- 关联资源是否存在。

Repository 不承担 HTTP 输入校验。

## Current Migration Policy

本规则落地后，现有 DTO / VO 允许增量迁移，不在 Design PR 中大规模改业务代码。

后续迁移顺序：

1. 全局时间 JSON Contract 已由 `yak-ops-boot/HttpJsonConfiguration` 落地。
2. Security `UserQueryDTO` 已迁移到 `PageQueryDTO`，旧 `PageParamDTO` 已删除。
3. Datasource `DataSourceQueryDTO` 已迁移到 `PageQueryDTO`，Datasource 不再维护重复分页常量。
4. 按真实需求接入 `SortDTO` 和 Repository 排序白名单。
5. 清理其他遗留的 `page / size / pageNum` 等分页字段。

## Must

- 新增 DTO / VO 默认遵守本规范。
- 公共分页和排序 Contract 只保留一套。
- API 字段命名保持 camelCase。
- 新增分页查询必须定义稳定默认排序。
- 时间格式通过全局配置保持一致。

## Must Not

- 新建第二套分页 DTO。
- 新建第二套排序 Contract。
- 让前端控制真实数据库列名。
- 使用 Entity 代替 DTO / VO。
- 为减少一行映射代码破坏分层边界。
- 在 DTO / VO 内定义 nested class / record / enum / interface。
