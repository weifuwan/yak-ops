# Common Rules

Scope:
- `yak-ops-common/**`

Depends On:
- `/JAVA_RULES.md`

Owns:
- Datasource shared DTO / VO / PO
- Datasource enums and constants
- unified Result / ErrorCode / BusinessException / PageData contracts
- small shared infrastructure with no better owner

## Compatibility Namespace

The package `io.yak.framework.common` is physically owned by `yak-ops-common`.

It keeps the original package name for current Yak Security binary compatibility.

Do not create a second copy under `io.yak.ops.common` until a deliberate namespace migration is designed.

## Must

- DTO 放在 `bean.dto.datasource` 或当前 Datasource 子能力对应的稳定包。
- VO 放在 `bean.vo.datasource`；SQL Execution 对外模型当前位于 `bean.vo.observability`。
- PO 只表达持久化结构，不承载业务行为。
- Datasource 枚举放在 `enums.datasource`。
- 统一 Result / ErrorCode / BusinessException / PageData Contract 只维护一份。
- 公共常量必须有真实跨类复用。
- 现有 MyBatis 公共配置能力优先复用。

## Must Not

- 重新依赖外部 `io.github.weifuwan:yak-common`。
- 在 Common 写 Datasource 业务编排。
- 把 Common 当作“暂时不知道放哪”的目录。
- 新增非 Datasource 领域对象。
- 重复定义已有 DTO / VO / enum / error contract。
- 重建已删除的测试代码。

## Boundary

Common 只保存稳定共享数据结构和小型基础能力。

能明确归属于 Datasource Business、Plugin 或 Boot 的代码，不进入 Common。
