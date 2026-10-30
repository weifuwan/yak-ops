# Yak Ops Engine

This module aligns its CLI and Composer source structure to **Apache Flink CDC 3.7**
(tag `release-3.7.0`) while preserving embedded YakFlow runtime semantics.

## Package alignment

| Yak Ops package | Upstream reference | Responsibility |
| --- | --- | --- |
| `io.yak.ops.cli.parser` | `flink-cdc-cli/.../cli/parser` | Pipeline YAML parser and `PipelineDef` creation |
| `io.yak.ops.cli.utils` | `flink-cdc-cli/.../cli/utils` | Upstream YAML and configuration flattening |
| `io.yak.ops.composer.definition` | `flink-cdc-composer/.../composer/definition` | Flink CDC 3.7 pipeline definitions |
| `io.yak.ops.composer` | `flink-cdc-composer/.../composer` | Composer contracts, augmented with trusted Task admission |
| `io.yak.ops.composer.yakflow` | `flink-cdc-composer/.../composer/flink` | Embedded YakFlow composer and deferred execution |
| `io.yak.ops.sync.runtime` | Platform-specific | Authorizations, resource planning, schema preparation and runtime |

**Inbound contract:** Frontend JSON → Backend API JSON → Task Plugin JSON-to-YAML →
Engine YAML Parser → Pipeline Definition → Composer → YakFlow `StreamGraph`.

The engine does not parse the product JSON. Flink-specific CLI launchers,
cluster deployment (YARN/Kubernetes), Flink DataStream translators and Flink
runtime lifecycle are **intentionally not copied**: Yak Ops runs YakFlow and
must preserve resource admission, instance registration and cancellation.
Unsupported operators must fail explicitly instead of being silently ignored.

The YakFlow-specific translation and platform-specific execution classes still
need a second, independent alignment pass. Do not treat directory renaming
as proof of behavioral parity.
