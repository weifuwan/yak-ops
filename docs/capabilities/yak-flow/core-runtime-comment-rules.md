# YakFlow Core / Runtime Javadoc Convention

Scope: `yak-ops-core/src/main/java/**`, `yak-flow/yak-flow-runtime/src/main/java/**`, `yak-flow/yak-flow-connector-base/src/main/java/**`, and their Java tests.

This file is the **module-specific override** to the `Comments and JavaDoc` section of `JAVA_RULES.md`. All other Java rules, including Spotless, remain in effect. It follows Flink's `JavadocType`, `JavadocMethod`, `JavadocParagraph` and `JavadocStyle` practices without requiring filler documentation.

## Language

- New and modified **Javadoc** and implementation comments use **English only**.
- Preserve original Java identifiers, protocol names, API references, and formal names such as `Checkpoint`, `SourceReader`, `SinkWriter`, `Mailbox`, `KeyGroup`.
- Do not translate log messages, exception text, data records, SQL, configuration keys or product-facing copy merely to satisfy comment conventions.
- Correct outdated descriptions as part of changing the related API; **do not** claim unsupported Flink features, distributed execution, exactly-once, committer, or rescaling.
- Existing, verifiable `@author` and `@since` provenance may remain. Do **not** invent a creator or creation date, or require these tags for new Core/Runtime types. Here, `@since` denotes an actual API version where known, not the edit date.

## Type Javadoc

Every repository-owned production **top-level class, interface, record, enum or annotation** has a Javadoc description. Meaningful nested public/protected types should also have it.

A useful type comment identifies:
- The owned responsibility and boundary (Core public contract vs. Runtime implementation).
- The important thread/lifecycle or mutable-state model when applicable.
- Important supported/unsupported behavior that a caller could otherwise misunderstand.

A one-sentence type comment is fine for a simple value type. Do not restate the Java type name or invent a paragraph merely to make the file look documented.

## Method Javadoc

- **Core public connector and execution APIs**: describe behavioral contracts, not just method names. Document non-obvious `@param`, `@return`, `@throws` and type parameters when they affect callers.
- **Runtime public/protected entrypoints**: document significant lifecycle transitions, mailbox/thread confinement, `CompletableFuture` completion/failure, Checkpoint ACK/durability, and ownership of resources.
- An implementation overriding a well-documented interface does not have to repeat the same explanation; use inherited Javadoc implicitly, or `{@inheritDoc}` only when extra text is helpful.
- Obvious getters/setters, constructors delegating to a documented overload, and simple internal methods do not require boilerplate Javadoc. Non-obvious private algorithms can use short `//` explanations instead.
- Tests primarily describe intent through behavior-oriented test names. Comment *why* a race, injected failure or checkpoint ordering matters rather than commenting every Arrange/Act/Assert step.

## Formatting

- Write a complete summary sentence ending in a period.
- Use `<p>` between independent paragraphs, and only when a second paragraph adds information.
- Use `{@link Type#method()}` for Java declarations and `{@code ...}` for literals, enum values and short code.
- Include a real description in tags; do not mechanically add `@return the return value` or `@throws Exception on error`.
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

- `scripts/ci/verify_yakflow_comments.py` enforces the **objective subset** on Core/Runtime production and test Java sources: no Chinese text inside comment tokens and Javadoc on production top-level Java types. It deliberately ignores Chinese string literals and Java text blocks.
- `Backend Quality` runs that checker as an **existing job step**, together with its regression self-tests. Do not create a second workflow/job or weaken Spotless.
- Human review covers quality, accuracy, method contracts, stale details and meaningless filler. Do not try to enforce subjective comment quality or demand tags on every getter with regex.
- Comment-only migrations must leave executable Java tokens and runtime behavior unchanged. Keep the original supported Checkpoint v1/v2 data formats intact.

## Source references

- Apache Flink: `flink-core/.../api/connector/source/Source.java`, `api/connector/sink2/Sink.java`.
- Apache Flink: `flink-runtime/.../streaming/runtime/tasks/mailbox/MailboxProcessor.java`, `runtime/source/coordinator/SourceCoordinator.java`.
- Apache Flink: `tools/maven/checkstyle.xml` JavadocType / JavadocMethod / JavadocParagraph / JavadocStyle checks.
