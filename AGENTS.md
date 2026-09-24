# Yak Ops Agent Context Router

Scope:
- Whole repository

Purpose:
- Route a task to the minimum required contracts and rules.
- Keep Datasource as the only active product domain.
- Prevent old or future architecture from being treated as current fact.

Engineering Model:
- `docs/engineering-context-model.md`

Before creating a new long-lived engineering document type, read the Engineering Context Model first.

## Backend Context

Any Java change starts with:

```text
ARCHITECTURE.md
JAVA_RULES.md
```

Load `BACKEND_TEST_RULES.md` when observable behavior, HTTP contracts, persistence, runtime behavior, plugin contracts or regression tests change.

Then load only the nearest rules touched by the task:

```text
**/controller/**
→ CONTROLLER_RULES.md

yak-ops-business/yak-ops-business-datasource/**
→ yak-ops-business/yak-ops-business-datasource/DATASOURCE_RULES.md

yak-ops-common/**
→ yak-ops-common/COMMON_RULES.md

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

Load `yak-ops-ui/TEST_RULES.md` when frontend behavior or regression tests change.

Load `yak-ops-ui/SERVICE_RULES.md` when changing backend API calls.

## Capability Context

Product behavior starts from:

```text
Task
→ docs/README.md
→ docs/capabilities/datasource/README.md
→ Target Capability when one exists
→ Target Code / Tests
→ Nearest Rules
```

Do not invent a Capability Contract from implementation guesses. If no capability document exists yet, inspect the current code and tests first, then write the contract before changing behavior.

## Execution Rules

Must:
- Read current code and direct dependencies before changing structure.
- Treat Datasource as the only active product domain.
- Reuse existing utilities and framework capabilities before adding abstractions.
- Solve only the current task.
- Prefer modifying existing code over adding layers.
- Load more context only when current evidence requires it.
- Validate the smallest meaningful behavior after the change.
- State clearly when tests or CI were not executed.

Must Not:
- Reintroduce removed domains, plugins or compatibility layers.
- Add Manager / Coordinator / Handler / Assembler / Adapter only for architectural symmetry.
- Duplicate an existing utility or framework capability.
- Treat a planned design as current implementation.
- Use old Git history or deleted documentation as current architecture unless the task explicitly requires historical analysis.

## Default Context

```text
Task goal
+ Capability Contract when behavior is involved
+ ARCHITECTURE.md
+ JAVA_RULES.md or FRONTEND_RULES.md
+ nearest module rules
+ target code
+ direct dependencies
+ relevant tests
```

**Locate first. Load only what constrains the task. Change only what the task owns.**
