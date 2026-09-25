# Java Rules

Scope:
- All Yak Ops Java production code

Load With:
- `ARCHITECTURE.md`
- nearest module RULES

Principles:
- 简单
- 紧凑
- 统一
- 先复用，后抽象
- 复杂度必须证明自己有必要存在

## Formatter Baseline

Java production code uses Spotless + Palantir Java Format as the mechanical formatting baseline.

Pinned versions:
- Spotless Maven Plugin: `3.10.2`
- Palantir Java Format: `2.97.0`

The backend production Java baseline has been fully formatted. Spotless no longer uses a ratchet; all backend Java sources are expected to remain clean.

Repository commands:

```bash
bash mvnw -DskipTests -pl '!yak-ops-bom,!yak-ops-ui,!yak-ops-dist' com.diffplug.spotless:spotless-maven-plugin:3.10.2:apply
bash mvnw -DskipTests -pl '!yak-ops-bom,!yak-ops-ui,!yak-ops-dist' com.diffplug.spotless:spotless-maven-plugin:3.10.2:check
```

`yak-ops-bom` is excluded because it is an independent BOM and does not inherit the root formatter plugin. UI and Dist are outside the backend Java formatting scope.

Rules:
- Run `spotless:apply` after changing Java layout or imports.
- Run `spotless:check` before declaring Java work verified.
- `spotless:check` is bound to the Maven `verify` lifecycle; backend verification cannot pass with unformatted production Java.
- CI is check-only. It must never run `spotless:apply`, rewrite source files or commit formatter output.
- Do not manually fight formatter output for indentation, wrapping, braces or imports.
- Formatter owns mechanical layout. `JAVA_RULES.md`, `ARCHITECTURE.md` and module RULES own naming, abstraction, layering and behavior.

## Backend Physical Quality Gate

Backend rules that can be determined mechanically are enforced by tools rather than AI review.

CI exposes three explicit gates:

```text
Backend format
      ↓
Backend compile
      ↓
Backend verify
```

Ownership:

- `Backend format` runs Spotless check directly so formatting failures are visible as their own CI step.
- `Backend compile` proves the selected backend Maven reactor compiles with Java 21.
- `Backend verify` runs the Maven verification lifecycle; Spotless check is also bound to this lifecycle as a repository-level invariant.
- `yak-ops-bom` remains outside the explicit formatter command because it does not inherit the Yak Ops root formatter plugin.
- `yak-ops-ui` and `yak-ops-dist` remain outside the backend verification reactor because frontend and distribution have separate build ownership.

Local backend verification:

```bash
bash mvnw -DskipTests -pl '!yak-ops-ui,!yak-ops-dist' verify
```

The explicit formatter command remains useful for fast local feedback, while `verify` is the final Maven lifecycle gate.

## Layout

- Use 4 spaces for Java indentation; never use tabs.
- Prefer one line when the complete expression remains clear and the formatter keeps it on one line.
- Do not manually break a constructor call, method call, assignment, return expression or fluent call that the formatter keeps readable on one line.
- When wrapping is necessary, accept formatter output instead of hand-aligning continuation lines.
- Opening braces stay with the declaration or control statement; do not create hanging brace-only layouts from manual wrapping.
- Keep one blank line between logical sections, not between every statement.
- No trailing whitespace. Every Java file ends with a newline.
- Wildcard imports are forbidden.

## Imports

- Use imports instead of fully-qualified class names inside ordinary code unless qualification is necessary to resolve ambiguity.
- Remove unused imports.
- Do not manually group imports with decorative blank lines that the formatter will remove or rewrite.
- Static imports are allowed only when they make the call site clearer.

## Class Structure

Prefer this order when applicable:

```text
class declaration
→ constants / static fields
→ instance fields
→ constructors
→ public methods
→ package-private / protected methods
→ private methods
```

Do not reorder code only for ceremony when keeping related behavior together is clearer.

## Constants

常量按 ownership 归属，不因为“可能复用”就提前提升为全局常量。

Must:
- 只被当前类使用的固定值保留为 `private static final`。
- 跨领域、跨模块共享且属于稳定代码契约的值统一放入 `io.yak.ops.common.constant.CommonConstants`。
- 只属于某个领域的共享固定值放入该领域的 `XxxConstants`；领域常量可以基于 `CommonConstants` 组合，例如 `DataSourceConstants.API_PREFIX = CommonConstants.API_PREFIX + "/data-source"`。
- 类型、状态、模式、阶段等有限值集合使用 `enum`，不要用一组 String / Integer constants 模拟枚举。
- 会随环境、部署或运行参数变化的值使用 `application.yml`、`@ConfigurationProperties` 或其他明确配置机制，不得硬编码为公共常量。
- 公共分页默认值、分页上限、全局 API Prefix 等全局约定只保留一个定义。

Must Not:
- 新增 constant interface 或通过 implements 暴露常量。
- 把 Datasource、Security 等领域语义塞进 `CommonConstants`。
- 新增与 `CommonConstants` 职责重叠的 `SystemConstants`、`ApiConstants`、`PageConstants` 等全局垃圾桶。
- 为只在一个类使用的值创建新的 Constants 类。
- 把可配置项、数据库状态值或业务类型仅为了“统一”提升为公共常量。

判断顺序：

```text
当前类独占
→ private static final

领域内共享
→ XxxConstants

跨领域稳定契约
→ CommonConstants

有限状态 / 类型
→ enum

运行时可变
→ configuration
```

## Type File Boundary

新增生产 Java 类型默认遵循“一种语义，一个顶层文件”。

Must:
- class / record / enum / interface / annotation 有独立语义时定义为顶层类型，并放在明确 owner package。
- DTO、VO、分页响应、公共 Contract、SPI Contract 必须使用顶层类型。
- 修改到已有 nested contract 时，应在同一变更中优先拆为顶层类型。

Must Not:
- 新增 nested class、nested record、nested enum、nested interface 作为业务或跨层 Contract。
- 为了“只在一个地方使用”把有独立字段和语义的数据结构塞进另一个类。
- 用 `Outer.Inner` 作为 Controller / Business / Repository / SPI 之间的公开类型。

Existing implementation-only nested types are migration debt and must not be expanded. Configuration holder、private cache state 等遗留内部类型可在后续独立重构中清理，不应作为新增 nested type 的先例。

## Null / Blank / Collection Handling

空值语义必须统一，避免每个领域重复维护自己的 `isBlank / normalize / requireText`。

Must:
- 字符串空白判断统一使用 `io.yak.ops.common.util.StringUtils.isBlank / isNotBlank`。
- 需要把空白字符串归一化为 `null` 时统一使用 `StringUtils.trimToNull`。
- 集合空判断统一使用 `io.yak.ops.common.util.CollectionUtils.isEmpty / isNotEmpty`。
- 需要统一表达对象空值时使用 `io.yak.ops.common.util.ObjectUtils.isNull / isNotNull`；需要保留 JDK 非空断言语义时使用 `ObjectUtils.requireNonNull`。
- 简单同名 Bean 属性复制统一使用 `BeanCopyUtils`，领域代码不得重复创建 `CopyBeanUtil / BeanUtil`。
- 通用 JSON parse / serialize 统一使用 `JSONUtils`；领域 owner 负责把通用 JSON 异常转换成自己的业务异常。

Must Not:
- 在业务模块重复实现 `value == null || value.trim().isEmpty()`、`hasText` 包装或 `trimToNull` 私有方法。
- 为普通判空创建 capability-local Utils。
- 为通用 JSON 解析自行维护新的 `ObjectMapper`。
- 用 Common Utils 承载领域校验文案、错误码或业务异常转换。

`Objects.equals`、`instanceof`、明确的状态比较等 JDK 语义不需要为了“统一”再包一层。

## Methods

- A method should express one clear operation.
- Current-class behavior that has no independent contract stays as a `private` method.
- Prefer early return when it removes nesting.
- Avoid methods that only forward the same arguments to another method without adding semantics.
- Avoid repeated null checks when the boundary already guarantees the value.
- Prefer existing JDK / Spring / MyBatis-Plus APIs over wrapper methods with identical semantics.
- Use `record` for immutable data carriers when it makes the contract clearer.

## Spring

- Spring-managed dependencies use `jakarta.annotation.Resource` field injection.
- Prefer `@Resource` by type for a single implementation; use `@Resource(name = "...")` only when bean identity must be explicit.
- Do not use constructor injection only to receive Spring-managed dependencies.
- Do not use Lombok `@RequiredArgsConstructor` as a dependency-injection mechanism.
- Do not use `@Autowired` for ordinary application Bean injection.
- A field injected by Spring is not `final`; object-owned immutable state, constants, caches, executors and value-object state should still use `final` when appropriate.
- `@Value`, `@ConfigurationProperties` state and `@Bean` method parameters are configuration/wiring mechanisms, not replacements for ordinary `@Resource` Bean fields.
- Application-level Spring wiring belongs to `yak-ops-boot` unless a module rule explicitly owns the configuration.
- Do not create manual Bean wiring when component scanning or existing Boot configuration already provides the same stable behavior.
- Keep capability code independent from Boot.
- Framework annotations should describe real runtime behavior, not compensate for unclear ownership.

## Lombok

- Lombok is allowed when it removes obvious boilerplate.
- Prefer focused annotations such as `@Getter` and `@RequiredArgsConstructor`.
- Do not use Lombok `@Slf4j`; logging follows `LOGGING_RULES.md` and declares SLF4J `Logger` explicitly.
- Do not use Lombok when generated behavior hides important invariants, lifecycle rules or security-sensitive behavior.
- Do not add Lombok only to save one trivial method if it makes the class contract less obvious.

## Logging

Production logging follows `LOGGING_RULES.md`.

Must:
- Use SLF4J `Logger` + `LoggerFactory`; declare `private static final Logger LOG = LoggerFactory.getLogger(Xxx.class)`.
- Repository-owned log messages use Chinese event descriptions with stable English keys, for example `"数据源创建完成，dataSourceId={}, type={}"`.
- Use SLF4J `{}` placeholders instead of string concatenation or `String.format`.
- Log exceptions once at the final handling boundary; a layer that only wraps and rethrows an exception must not log the same failure first.
- Never log passwords, tokens, authorization headers, cookies, session identifiers, secret keys, unmasked connection JSON, credential-bearing JDBC URLs or complete request/data objects.

Must Not:
- Use `System.out`, `System.err` or `printStackTrace` for application diagnostics.
- Add `LogUtils`, custom logger factories, logging managers or other wrappers around SLF4J.
- Add method-entry/method-exit logs, ordinary read/query logs or loop-level INFO logs only to make execution look observable.
- Use `TRACE` in V1 application code.

## Naming and Abstraction

Names must describe ownership or business meaning, not generic technical activity.

Must Not:
- Add `Manager`, `Coordinator`, `Handler`, `Assembler`, `Adapter`, `Helper` or `Base` without real ownership.
- Rename an existing class only because its suffix appears on the forbidden list; first verify whether it already has a legitimate contract or boundary.
- Add `interface + impl` only for symmetry.
- Split a class only because it is long.
- Add pass-through layers.
- Create future-facing extension points without a current caller.

Existing names such as `AuthenticationManager`, capability-specific exception handlers or protocol adapters may remain when they satisfy the Abstraction Test.

## Comments and JavaDoc

Comments exist to explain information that code cannot express clearly by itself. They must describe responsibility, semantics, constraints, boundaries or reasons instead of translating names and syntax into prose.

### Type JavaDoc

- Repository-owned production Java classes, interfaces, enums, records and annotations must have concise type-level JavaDoc.
- Type JavaDoc must explain what the type is responsible for or what boundary it represents.
- Do not use descriptions such as "user service", "datasource DTO", "CRUD service" or "implementation class" when they only repeat the type name or technical suffix.
- Type JavaDoc must include the original author with `@author`.
- Type JavaDoc must include the creation date with `@since YYYY-MM-DD`.
- `@author` records the original creator. Later contributors must not replace it when modifying the type.
- `@since` records the type creation date. Later changes must not update it to the modification date.
- Prefer Chinese descriptions. Keep established technical terms, protocol names, table names and identifiers in their original form.

Example:

```java
/**
 * 负责用户账号的创建、修改和密码重置等后台管理能力。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public class UserServiceImpl {
}
```

### Data Model Fields

- DTO, VO, Entity and Model fields must have JavaDoc describing their business meaning.
- A field comment must add semantic information, not only repeat the field name.
- When a field has enumerated values, status codes, units, formats, ranges, sensitive-data semantics or other special constraints, document them explicitly.
- The same rule applies to equivalent repository-owned request, response, command, criteria and persistent data carriers when their fields form an external or cross-layer data contract.
- Ordinary dependency fields, loggers, framework wiring and obvious implementation state do not require comments solely for completeness.

Example:

```java
/** 用户状态：1 启用，0 禁用。 */
private Integer status;

/** 数据源连接参数，JSON 格式。 */
private String connectionParams;
```

### Method JavaDoc

- Do not require JavaDoc for every method.
- Public contracts, SPI methods and non-obvious business behavior should document important preconditions, side effects, invariants, exceptional semantics or usage boundaries.
- Private methods and straightforward application methods do not need JavaDoc when the method name and code already make their purpose clear.
- Do not add getter/setter JavaDoc or comments that only restate a method name, parameter name or return type.

### Comment Quality

Must:
- Keep comments synchronized with current behavior.
- Explain responsibility, semantic meaning, constraint, boundary or reason.
- Delete stale comments when the related behavior or architecture is removed.

Must Not:
- Use comments such as "用户 Service", "数据源 DTO", "XXX CRUD" or "获取用户方法" that add no information.
- Add comments to `@Resource` dependencies, loggers or similar obvious fields only to satisfy a comment count.
- Add verbose getter/setter comments.
- Keep historical architecture descriptions that no longer match the code.

Formatter does not rewrite JavaDoc content; comment quality remains a review concern.

## Must

- 先读当前类和直接依赖，再决定新增结构。
- 当前类用 `private` 方法能收敛时，优先 private method。
- JDK、Spring、MyBatis-Plus 或现有工具已经提供的能力直接复用。
- 一个类只承担一个明确 owner 的职责。
- 不可变数据适合 `record` 且更清晰时优先使用 `record`。
- Lombok 能明显减少样板代码时可以使用，但不能隐藏关键行为。
- 仓库自有生产 Java 类型必须有类级 JavaDoc；DTO / VO / Entity / Model 等数据模型字段必须有语义明确的 JavaDoc。
- 删除过期注释和已经不存在的架构描述。
- 修改完成后执行与改动匹配的显式编译、构建、静态检查或手工验证。
- Java 变更至少执行上面的 Spotless `check` 命令；需要修复格式时先执行对应的 `apply` 命令。

## Must Not

- 没有真实 ownership 时新增 Manager / Coordinator / Handler / Assembler / Adapter / Helper / Base。
- 为了文件长度拆类。
- 为了“统一架构”增加无意义 interface + impl。
- 增加纯转发、重复判空、重复转换、重复赋值。
- 重复封装框架已有能力。
- 为未来需求提前创建空层、空接口或扩展点。
- 重建已删除的测试或 CI 体系作为任务副作用。
- 为通过 formatter 改变业务行为。
- 新增 nested class / record / enum / interface 承载业务或跨层 Contract。

## Abstraction Test

新增一个类或层之前至少能回答一个问题：

- 它是否拥有独立生命周期？
- 它是否拥有稳定 Contract？
- 它是否隔离外部协议或基础设施？
- 它是否拥有明确业务规则？
- 它是否被多个真实调用方以同一语义复用？

如果都不是，优先不抽。

## Verification

Formatting is necessary but not sufficient.

For a Java change, verification should match the touched boundary:

```text
mechanical style
→ repository Spotless check command above

compile-sensitive change
→ relevant Maven compile command

backend lifecycle verification
→ bash mvnw -DskipTests -pl '!yak-ops-ui,!yak-ops-dist' verify

behavior change
→ capability-specific manual or automated verification
```

Never claim compile, test or runtime verification that was not actually executed.
