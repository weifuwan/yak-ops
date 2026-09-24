# Yak Ops Agent Context Router

Scope:
- Whole repository

Purpose:
- Route a task to the minimum required contracts and rules.
- Keep Datasource as the only active product domain.
- Prevent removed architecture from becoming new context.

Engineering Model:
- `docs/engineering-context-model.md`

## Backend Context

Any Java change starts with:

```text
ARCHITECTURE.md
JAVA_RULES.md
```

Then load only the nearest rules touched by the task:

```text
**/controller/**
→ CONTROLLER_RULES.md

yak-ops-business/yak-ops-business-datasource/**
→ yak-ops-business/yak-ops-business-datasource/DATASOURCE_RULES.md

yak-ops-common/**
→ yak-ops-common/COMMON_RULES.md

yak-ops-dao/**
→ yak-ops-dao/DAO_RULES.md

yak-ops-core/**
→ yak-ops-core/CORE_RULES.md

yak-ops-spi/**
→ yak-ops-spi/SPI_RULES.md

yak-ops-plugins/yak-ops-plugin-datasource/**
→ yak-ops-plugins/yak-ops-plugin-datasource/PLUGIN_RULES.md
```

## Frontend Context

Any frontend change starts with:

```text
yak-ops-ui/ARCHITECTURE.md
yak-ops-ui/FRONTEND_RULES.md
```

Load `yak-ops-ui/SERVICE_RULES.md` when changing backend API calls.

## Capability Context

```text
Task
→ docs/README.md
→ docs/capabilities/datasource/README.md
→ Target Capability when one exists
→ Target Code
→ Nearest Rules
```

If no Capability Contract exists, inspect current code first and write the minimum contract before changing product behavior.

## Execution Rules

Must:
- Read current code and direct dependencies before changing structure.
- Treat Datasource as the only active product domain.
- Reuse existing utilities and framework capabilities before adding abstractions.
- Solve only the current task.
- Prefer modifying existing code over adding layers.
- Validate the smallest meaningful result with explicit local compile/build/manual verification when needed.
- State exactly what verification was or was not executed.

Must Not:
- Reintroduce removed domains, plugins, tests or CI as a side effect.
- Add Manager / Coordinator / Handler / Assembler / Adapter only for symmetry.
- Treat future design as current implementation.
- Use deleted documentation or old Git history as current architecture unless historical analysis is explicitly requested.

## Default Context

```text
Task goal
+ Capability Contract when behavior is involved
+ ARCHITECTURE.md
+ JAVA_RULES.md or FRONTEND_RULES.md
+ nearest module rules
+ target code
+ direct dependencies
```

**Locate first. Load only what constrains the task. Change only what the task owns.**
