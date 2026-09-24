# Yak Ops Docs

这是 Yak Ops 的文档入口。

当前产品范围：

```text
Datasource
```

Supporting platform capability:

```text
User / Login / Security
```

## Engineering Context Model

```text
What to build       → Capability Contract
How to code         → *_RULES.md
Where things belong → ARCHITECTURE.md
How to prove        → Explicit Verification
```

完整定义见 [Engineering Context Model](./engineering-context-model.md)。

## Current Knowledge Map

- [Datasource](./capabilities/datasource/README.md)
- Security rules: [yak-ops-security/SECURITY_RULES.md](../yak-ops-security/SECURITY_RULES.md)

Security is a supporting platform capability, not a second product domain.

## Context Loading

```text
Task
→ target capability
→ target code / data
→ nearest RULES
→ implementation
→ explicit verification
```

## Engineering Rules

- [Architecture](../ARCHITECTURE.md)
- [Java Rules](../JAVA_RULES.md)
- [Controller Rules](../CONTROLLER_RULES.md)
- [Security Rules](../yak-ops-security/SECURITY_RULES.md)
- [Datasource Rules](../yak-ops-business/yak-ops-business-datasource/DATASOURCE_RULES.md)
- [Common Rules](../yak-ops-common/COMMON_RULES.md)
- [Core Rules](../yak-ops-core/CORE_RULES.md)
- [SPI Rules](../yak-ops-spi/SPI_RULES.md)
- [Datasource Plugin Rules](../yak-ops-plugins/yak-ops-plugin-datasource/PLUGIN_RULES.md)
- [Frontend Architecture](../yak-ops-ui/ARCHITECTURE.md)
- [Frontend Rules](../yak-ops-ui/FRONTEND_RULES.md)
- [Frontend Service Rules](../yak-ops-ui/SERVICE_RULES.md)
- [Frontend Tooling](../yak-ops-ui/docs/tooling.md)
