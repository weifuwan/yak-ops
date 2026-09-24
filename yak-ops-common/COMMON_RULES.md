# Common Rules

Scope:
- `yak-ops-common/**`

Depends On:
- `/JAVA_RULES.md`
- public behavior changes also load `/BACKEND_TEST_RULES.md`

Owns:
- Datasource shared DTO / VO / PO
- Datasource enums and constants
- unified Result / ErrorCode / BusinessException / PageData contracts
- small shared infrastructure with no better owner

## Compatibility Namespace

The package `io.yak.framework.common` is now physically owned by `yak-ops-common`.

It is intentionally kept with the original package name so the current Yak Security binary remains compatible without pulling the external `yak-common` jar.

Do not create a second copy under `io.yak.ops.common` until a deliberate namespace migration is designed.

## Must

- DTO 放在 `bean.dto.datasource` 或当前 Datasource 子能力对应的稳定包。
- VO 放在 `bean.vo.datasource`；SQL Execution 对外模型当前位于 `bean.vo.observability`。
- PO 只表达持久化结构，不承载业务行为。
- Datasource 枚举放在 `enums.datasource`。
- 统一 Result / ErrorCode / BusinessException / PageData Contract 只维护一份。
- 公共常量必须有真实跨类复用，不为单个调用点提前抽常量类。
- Validation、Swagger Schema 和持久化注解只描述对应边界事实。
- 现有 MyBatis 公共配置能力优先复用。

## Must Not

- 重新依赖外部 `io.github.weifuwan:yak-common`。
- 在 Common 写 Datasource 业务编排。
- 把 Common 当作“暂时不知道放哪”的目录。
- 新增非 Datasource 领域对象。
- 重复定义 Datasource 已有 DTO / VO / enum / error contract。
- 为了跨模块方便把领域行为塞进 DTO / VO / PO。
- 扩展历史非 Datasource residue；需要碰到时优先评估删除。

## Boundary

Common 只保存稳定共享数据结构和小型基础能力。

能明确归属于 Datasource Business、Plugin 或 Boot 的代码，不进入 Common。
