# DolphinScheduler JDBC Registry source

- Upstream repository: https://github.com/apache/dolphinscheduler
- Source baseline: released tag **3.4.3** (Apache License 2.0).
- Preserved package names: `org.apache.dolphinscheduler.registry.api` and
  `org.apache.dolphinscheduler.plugin.registry.jdbc`.
- All JDBC Registry production Java classes are copied from the upstream module,
  together with `mysql_registry_init.sql`, `postgresql_registry_init.sql`,
  and the upstream `META-INF/spring.factories` resource.
- Used SPI source is copied into `yak-ops-registry-api`. Upstream RegistryClient
  and HA classes are outside the JDBC module's runtime dependency graph and not
  copied; they require DolphinScheduler Master/Worker heartbeat models.
- `ThreadUtils` and `DefaultUncaughtExceptionHandler` are copied unchanged;
  `CodeGenerateUtils` is copied with its single process-ID compatibility change.
- **Compatibility changes**: use Java 21 `ProcessHandle.current().pid()`
  in the upstream CodeGenerateUtils and JDBC client/lock utility; replace
  three `NetUtils.getHost()` uses with the narrow `JdbcRegistryHost` helper.
  Upstream NetUtils is intentionally not copied: its unrelated helpers depend
  on KubernetesUtils/PropertyUtils and additional DolphinScheduler subsystems.
  Two DTOs use the local `JdbcRegistryJson` adapter rather than the full
  DolphinScheduler Common JSONUtils module.
- Spring Boot 3 auto-configuration discovery is added in
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`;
  it points to the original upstream configuration class.
- **Schema lifecycle**: Yak Ops Flyway V1 owns creation of the four upstream
  Registry tables. The original SQL resources are retained for source comparison
  and must not be re-executed after migration. Existing V1 databases require an
  explicit migration or clean rebuild; changing V1 alone is not a safe upgrade.

Do not rename imported upstream classes or selectively rewrite algorithms.
