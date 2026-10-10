# YakFlow Javadoc and Comment Convention

Scope: `yak-ops-core/src/main/java/**`, `yak-flow/yak-flow-runtime/src/main/java/**`, `yak-flow/yak-flow-connector-base/src/main/java/**`, `yak-flow/yak-flow-connector-jdbc/src/main/java/**`, `yak-flow/yak-flow-connector-cdc-mysql/src/main/java/**`, and their Java tests.

This file is the **YakFlow-wide override** to the `Comments and JavaDoc` section of `JAVA_RULES.md`. It applies uniformly to Core and **all modules under `yak-flow/`**, including Connector Base, JDBC Source/Sink, and MySQL CDC. All other Java rules, including Spotless, remain in effect. It follows Flink's `JavadocType`, `JavadocMethod`, `JavadocParagraph` and `JavadocStyle` practices without requiring filler documentation.

The file path remains unchanged for existing references; `core-runtime` in the filename does **not** narrow its scope.

| Module | API documentation emphasis |
| --- | --- |
| `yak-ops-core` | Source/Sink SPI, RowData/Schema contracts, configuration and lifecycle capabilities |
| `yak-flow-runtime` | Task mailbox, graph construction, events, checkpoint, cancellation and ownership |
| `yak-flow-connector-base` | Source fetch/emit handover, split progress and batch buffer/flush guarantees |
| `yak-flow-connector-jdbc` | Factory/Catalog/Dialect/Converter SPI, JDBC Source enumeration/reading, Sink transaction and statement lifecycle |
| `yak-flow-connector-cdc-mysql` | Debezium Binlog Reader, bounded handover, offset/checkpoint and schema-history recovery contracts |

## Language

- New and modified **Javadoc** and implementation comments use **English only**.
- Preserve original Java identifiers, protocol names, API references, and formal names such as `Checkpoint`, `SourceReader`, `SinkWriter`, `Mailbox`, `KeyGroup`.
- Do not translate log messages, exception text, data records, SQL, configuration keys or product-facing copy merely to satisfy comment conventions.
- Correct outdated descriptions as part of changing the related API; **do not** claim unsupported Flink features, distributed execution, exactly-once, committer, or rescaling.
- Existing, verifiable `@author` and `@since` provenance may remain. Do **not** invent a creator or creation date, or require these tags for any YakFlow module. Here, `@since` denotes an actual API version where known, not the edit date.

## Type Javadoc

Every repository-owned production **top-level class, interface, record, enum or annotation** has a Javadoc description. Meaningful nested public/protected types should also have it.

A useful type comment identifies:
- The owned responsibility and boundary (Core contract, Runtime mechanism, Connector Base primitive, or JDBC-specific implementation).
- The important thread/lifecycle or mutable-state model when applicable.
- Important supported/unsupported behavior that a caller could otherwise misunderstand.

A one-sentence type comment is fine for a simple value type. Do not restate the Java type name or invent a paragraph merely to make the file look documented.

## Method Javadoc

- **Core public connector and execution APIs**: describe behavioral contracts, not just method names. Document non-obvious `@param`, `@return`, `@throws` and type parameters when they affect callers.
- **Runtime public/protected entrypoints**: document significant lifecycle transitions, mailbox/thread confinement, `CompletableFuture` completion/failure, Checkpoint ACK/durability, and ownership of resources.
- **Connector Base public SPI and extension points**: document record ownership (one buffer only), blocking fetcher versus mailbox execution, completion markers, nonblocking availability, size/timer/checkpoint flush, and whether close/cancel may flush or commit.
- **JDBC contracts**: document identifier quoting, schema/column ordering, native SQL placeholders, source split/replay and schema fingerprints, write mode and RowKind semantics, connection and statement ownership, transaction boundaries, retries and cancellation.
- **JDBC factory/dialect/catalog/converter interfaces**: document each nontrivial public method's preconditions and observable result. Include useful `@param`/`@return`/`@throws` tags for public operations whose meaning, ordering or failure modes are not obvious. A type-level description alone is **not sufficient** for an SPI with distinct methods.
- **Concrete JDBC implementations**: describe vendor-specific differences at class level and in overridden methods only where behavior differs from the documented interface. Use inherited Javadoc for straightforward overrides; do not duplicate the entire interface contract.
- An implementation overriding a well-documented interface does not have to repeat the same explanation; use inherited Javadoc implicitly, or `{@inheritDoc}` only when extra text is helpful.
- Obvious getters/setters, constructors delegating to a documented overload, and simple internal methods do not require boilerplate Javadoc. Non-obvious private algorithms can use short `//` explanations instead.
- Tests primarily describe intent through behavior-oriented test names. Comment *why* a race, injected failure or checkpoint ordering matters rather than commenting every Arrange/Act/Assert step.

## Formatting

- Write a complete summary sentence ending in a period. For public contracts, lead with what the operation **guarantees**, not "Gets/Creates/Handles the X".
- Use `<p>` between independent paragraphs, and only when a second paragraph adds information.
- Use `{@link Type#method()}` for Java declarations and `{@code ...}` for literals, enum values and short code.
- Include a real description in tags; do not mechanically add `@return the return value` or `@throws Exception on error`.
- Use `@see` where it actually helps callers locate an adjacent API or Flink-inspired contract; do **not** imply YakFlow implements Flink classes or refer to unavailable Flink types in JavaDoc links.
- Document supported behavior precisely: e.g. JDBC Source bounded at-least-once split replay is **not** a consistent full-database snapshot; JDBC Sink synchronous flush commits at-least-once, never XA or exactly-once. A close or cancel must not implicitly flush.
- Maintain correct Markdown/HTML entities inside Javadoc and avoid invalid HTML.
- Keep inline `//` comments short and immediately next to the relevant invariant.

## Good examples

**Core SPI:**

```java
/**
 * Polls available records without blocking the task mailbox.
 *
 * <p>Return {@link InputStatus#NOTHING_AVAILABLE} when no data is ready; the
 * runtime waits for {@link #isAvailable()} before polling again.
 *
 * @param output receives the records emitted by this call
 * @return the availability status after the current input step
 * @throws Exception if reading or delivering a record fails
 */
InputStatus pollNext(ReaderOutput<T> output) throws Exception;
```

**JDBC Dialect contract (SQL placeholder ordering):**

```java
/**
 * Builds a parameterized INSERT statement using the target schema's column order.
 *
 * <p>Each column contributes exactly one JDBC placeholder. The caller binds values
 * in the same order through the dialect's row converter.
 *
 * @param table the target table, including its optional catalog/schema
 * @param schema the resolved target columns in binding order
 * @return an INSERT statement with one placeholder per column
 */
String insertSql(TableId table, TableSchema schema);
```

**Connector Base lifecycle:**

```java
/**
 * Flushes records that have reached the output-owned buffer.
 *
 * <p>Checkpoint and end-of-input callers must reject an incomplete logical update.
 * Closing the output never implies a successful flush or transaction commit.
 */
void flush() throws Exception;
```

**Runtime concurrency:**

```java
// Post-barrier records must not exhaust the buffer before other producers align.
```

**Avoid:**

```java
/** Gets the task status. */
JobStatus getStatus();

// Set flag to true.
finished = true;
```

## Guardrails and CI

- `scripts/ci/verify_yakflow_comments.py` already covers **all four modules**, including both JDBC and Connector Base, for the **objective subset**: no Chinese text in Java comment tokens and required production top-level type Javadoc. It deliberately ignores Chinese string literals and Java text blocks.
- `Backend Quality` runs that checker as an **existing job step**, together with its regression self-tests. Do not create a second workflow/job or weaken Spotless.
- Human review covers quality, accuracy, method contracts, stale details, misleading `@see` references and meaningless filler. Do not enforce subjective comment quality or demand tags on every getter with regex. A PR touching public SPI behavior must review the adjacent method JavaDoc.
- Comment-only migrations must leave executable Java tokens and runtime behavior unchanged. Keep the original supported Checkpoint v1/v2 data formats intact.

## Source references

- Apache Flink: `flink-core/.../api/connector/source/Source.java`, `api/connector/sink2/Sink.java`.
- Apache Flink: `flink-runtime/.../streaming/runtime/tasks/mailbox/MailboxProcessor.java`, `runtime/source/coordinator/SourceCoordinator.java`.
- Apache Flink JDBC Connector: `flink-connector-jdbc-core/.../core/database/dialect/JdbcDialect.java`, `internal/JdbcOutputFormat.java`, `internal/executor/JdbcBatchStatementExecutor.java`.
- Apache Flink: `tools/maven/checkstyle.xml` JavadocType / JavadocMethod / JavadocParagraph / JavadocStyle checks.
