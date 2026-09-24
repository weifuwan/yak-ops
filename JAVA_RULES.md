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
- Do not manually fight formatter output for indentation, wrapping, braces or imports.
- Formatter owns mechanical layout. `JAVA_RULES.md`, `ARCHITECTURE.md` and module RULES own naming, abstraction, layering and behavior.

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
→ nested types
```

Do not reorder code only for ceremony when keeping related behavior together is clearer.

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
- Prefer focused annotations such as `@Getter`, `@RequiredArgsConstructor` and `@Slf4j`.
- Do not use Lombok when generated behavior hides important invariants, lifecycle rules or security-sensitive behavior.
- Do not add Lombok only to save one trivial method if it makes the class contract less obvious.

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
public class UserAdministrationService {
}
```

### Data Model Fields

- DTO, VO, PO, Entity and Model fields must have JavaDoc describing their business meaning.
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
- 仓库自有生产 Java 类型必须有类级 JavaDoc；DTO / VO / PO / Entity / Model 等数据模型字段必须有语义明确的 JavaDoc。
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
→ relevant Maven compile/package command

behavior change
→ capability-specific manual or automated verification
```

Never claim compile, test or runtime verification that was not actually executed.
