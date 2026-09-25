# SPI Rules

Scope:
- `yak-ops-spi/**`

Status:
- Reserved / Minimal

Depends On:
- `/ARCHITECTURE.md`
- `/JAVA_RULES.md`

## Current Fact

当前 Datasource 插件稳定 Contract 位于：

```text
yak-ops-plugins/yak-ops-plugin-datasource/
└── yak-ops-plugin-datasource-api
```

`yak-ops-spi` 目前只有最小包声明，不是 Datasource Plugin API 的第二套入口。

Datasource Plugin API 的 Java namespace 统一为 `io.yak.ops.plugin.datasource.api`；禁止重新创建 `io.yak.ops.spi.datasource` 兼容包。

## Must

- 只有出现真实跨插件、跨产品能力的稳定扩展协议时，才在这里新增 SPI。
- SPI 类型保持实现无关、业务编排无关。
- 新增前先证明为什么不能放在 `yak-ops-plugin-datasource-api`。

## Must Not

- 重建 Resource / Task SPI。
- 和 Datasource Plugin API 定义重复接口。
- 放 Repository、Controller、Spring Bean 编排或持久化对象。
- 为未来未知插件提前设计协议。

## Boundary

没有明确跨能力扩展需求时，保持 SPI 最小化。
