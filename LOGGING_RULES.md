# Logging Rules

Scope:
- All Yak Ops backend production logging
- Runtime logging configuration in `yak-ops-boot`

Load With:
- `JAVA_RULES.md`
- nearest module RULES when logging is added or changed inside that module

Principles:
- 日志为排障和运维服务，不为“看起来有日志”服务
- 重要事件留下线索，正常路径保持安静
- 同一个失败只记录一次
- 敏感数据永不进入日志

## Logging Stack

Yak Ops uses this fixed V1 stack:

```text
application code
→ SLF4J API
→ Spring Boot logging system
→ Logback runtime
→ console + logs/yak-ops.log
```

Rules:
- Production Java code depends on SLF4J APIs only.
- Logback is the runtime implementation supplied by Spring Boot.
- Application-level logging configuration belongs to `yak-ops-boot`.
- Do not call Logback, Log4j2 or java.util.logging APIs directly from capability code.
- Do not introduce another logging implementation unless a real runtime requirement cannot be met by the current Spring Boot + Logback baseline.
- V1 does not add `logback-spring.xml`; use Spring Boot logging properties while they are sufficient.

## Logger Declaration

Use an explicit class-owned logger:

```java
private static final Logger LOG = LoggerFactory.getLogger(DataSourceServiceImpl.class);
```

Must:
- Use `org.slf4j.Logger` and `org.slf4j.LoggerFactory`.
- Name the field `LOG`.
- Keep the logger `private static final`.

Must Not:
- Use Lombok `@Slf4j`.
- Add `LogUtils`, `LoggerManager`, custom logger factories or similar wrappers.
- Inject a logger as a Spring Bean.

SLF4J is already the abstraction boundary. Do not add another pass-through logging layer.

## Message Language and Shape

Repository-owned log messages use:

```text
中文事件描述，key={}, key={}
```

Example:

```java
LOG.info("数据源插件注册完成，type={}, capabilities={}", type, capabilities);
LOG.warn("登录态失效，operator={}, loginUserId={}", operator, loginUserId);
```

Rules:
- Event descriptions are Chinese.
- Stable technical field names remain English, such as `userId`, `dataSourceId`, `pluginType`, `requestId`.
- Use SLF4J `{}` placeholders.
- Keep messages short and searchable.
- Framework or third-party exception messages may remain in their original language inside stack traces; do not rewrite external exception text only for translation.

Must Not:

```java
LOG.info("创建数据源：" + dataSourceId);
LOG.info(String.format("创建数据源：%s", dataSourceId));
LOG.info("进入 createDataSource 方法");
LOG.info("查询完成");
```

## Log Levels

### ERROR

Use ERROR when:
- the requested operation cannot complete,
- the failure is unexpected or represents an internal/server-side fault,
- and the current location is the final handling boundary that owns logging the failure.

Do not use ERROR for normal validation failures, expected business rejection, 401/403/404, wrong passwords or ordinary duplicate input.

### WARN

Use WARN for an abnormal condition that is handled or recoverable but deserves operator attention.

Examples:
- a login state is inconsistent and is invalidated,
- a bootstrap administrator was created and the bootstrap switch should now be disabled,
- a fallback path was actually used because the preferred path was unavailable.

Do not turn ordinary user behavior into WARN noise.

### INFO

Use INFO only for operationally useful state changes.

Good candidates:
- important startup registration or readiness state,
- creation or mutation of important security/platform state,
- a long-lived resource entering a meaningful lifecycle state.

Ordinary reads, pagination, repository queries, method entry/exit and every internal step do not belong at INFO.

### DEBUG

Use DEBUG for temporary or detailed diagnostics that help development and incident investigation.

Rules:
- production defaults to INFO;
- enable DEBUG only when needed through runtime configuration;
- DEBUG does not permit sensitive data.

### TRACE

TRACE is not used by Yak Ops V1 application code.

## Placement and Noise Control

Before adding a log, answer:

```text
If this line disappears,
would production diagnosis or operation become materially harder?
```

If not, do not add it.

Must:
- Prefer one useful lifecycle log over several step-by-step logs.
- Log identifiers needed to correlate an event, not the entire object.
- Avoid INFO/WARN inside high-frequency loops unless each item is independently operationally important.

Must Not:
- Log every Controller request manually.
- Log every Service method entry and exit.
- Log successful ordinary reads.
- Log SQL in application code when the persistence framework already owns SQL diagnostics.
- Add logs only to increase log volume or perceived observability.

## Exception Logging Boundary

Core rule:

```text
catch + wrap + rethrow
→ do not log

final handling boundary
→ log once
```

A wrapping layer preserves the cause but does not print it. If the exception finally becomes an unexpected 5xx response, the HTTP exception boundary logs the stack trace once.

Rules:
- Expected business exceptions normally return/map their error without ERROR logging.
- Parameter validation failures do not print stack traces at INFO/WARN/ERROR.
- When a caught exception is completely recovered and not rethrown, WARN may be appropriate if the recovery itself is operationally important.
- Never print the same exception stack trace in both Service and Controller advice.

## Sensitive Data

Never log:
- passwords or password hashes,
- access tokens, refresh tokens or API tokens,
- Authorization headers,
- Cookie values,
- session identifiers,
- secret/access keys,
- database passwords,
- unmasked datasource connection JSON,
- credential-bearing JDBC URLs,
- complete HTTP requests,
- complete DTO / VO / Entity / Model objects when they may contain user or secret data.

If a value is required for diagnosis, log a stable identifier or an explicitly masked representation.

The restriction applies to DEBUG as well as INFO/WARN/ERROR.

## Runtime Baseline

Runtime ownership:
- `yak-ops-boot/src/main/resources/application.yml`

V1 defaults:
- console logging: enabled by Spring Boot,
- file logging: `logs/yak-ops.log`,
- application level: `INFO`,
- rolling: time + size,
- archive size threshold: `100MB`,
- history: `7`,
- total archive cap: `5GB`,
- archive compression: gzip.

Runtime overrides:
- `YAK_LOG_LEVEL`
- `YAK_LOG_FILE`
- `YAK_LOG_FILE_PATTERN`
- `YAK_LOG_MAX_FILE_SIZE`
- `YAK_LOG_MAX_HISTORY`
- `YAK_LOG_TOTAL_SIZE_CAP`

Example:

```bash
YAK_LOG_LEVEL=DEBUG java -jar yak-ops-boot.jar
```

The default level remains INFO. WARN and ERROR are included automatically by the logging threshold; they are not enabled through separate switches.

Runtime log files are deployment artifacts. They are not packaged into the application distribution and must not be committed to the repository.

## Must

- Use SLF4J + explicit `LoggerFactory`.
- Use Chinese repository-owned event text and stable English keys.
- Use `{}` parameterization.
- Keep production default at INFO.
- Preserve exception causes when translating exceptions.
- Print an unexpected failure once at its final boundary.
- Keep sensitive data out of every log level.
- Keep logging configuration in Boot unless a lower module owns a real independent runtime.

## Must Not

- Use `@Slf4j`.
- Use `System.out`, `System.err` or `printStackTrace`.
- Add another logging facade or wrapper.
- Add Log4j2 only for preference.
- Add `logback-spring.xml` before Spring Boot properties are insufficient.
- Split INFO/WARN/ERROR into separate files in V1 without an operational requirement.
- Log ordinary CRUD/read flow step by step.
- Log and rethrow the same exception toward another logging boundary.
- Log secrets, credentials or complete sensitive objects.

## Verification

For logging changes, verify the touched boundary:

```text
Java logger changes
→ repository Spotless check
→ relevant module compile

runtime logging properties
→ Boot configuration parses
→ application starts
→ logs/yak-ops.log is created
→ DEBUG can be enabled through YAK_LOG_LEVEL
→ rolling configuration is bound by Spring Boot
```

Do not claim runtime or rolling verification unless it was actually executed.
