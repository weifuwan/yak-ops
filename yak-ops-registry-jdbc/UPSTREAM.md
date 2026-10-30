# DolphinScheduler JDBC Registry source

- Upstream repository: https://github.com/apache/dolphinscheduler
- Source baseline: released tag **3.4.3** (Apache License 2.0); heartbeat/lease safety patch imported from upstream PR **#18416** (`a600f0659888f5f99deed1d3c464983f560a250b`).
- Preserved package names: `org.apache.dolphinscheduler.registry.api` and
  `org.apache.dolphinscheduler.plugin.registry.jdbc`.
- All JDBC Registry production Java classes are copied from the upstream module,
  together with the two SQL resources and the upstream `META-INF/spring.factories` resource.
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
  Registry tables. SQL resources, four DO `@TableName` annotations and Mapper SQL adapt only table/index identifiers from `t_ds_` to `yak_ops_`; preserve all upstream algorithms. The bootstrap SQL resources must not be re-executed after migration. Existing V1 databases require an
  explicit migration or clean rebuild; changing V1 alone is not a safe upgrade.

The JDBC Registry server, heartbeat Mapper/Repository, and upstream regression test follow Apache DolphinScheduler PR #18416 (compare-and-delete expiration, durable current heartbeat, atomic connection state). Keep provenance when updating further.

Do not rename imported upstream classes or selectively rewrite algorithms.

## Yak Ops orphan cleanup race fix (PR #1813 follow-up)

The 3.4.3-derived purge implementation previously used a stale heartbeat snapshot to decide
whether to delete *all* data/locks collected later. A client that registered between
those queries could lose its live ephemeral node or lock. Purge now uses owner-checked,
heartbeat-absence-guarded SQL DELETEs. Ephemeral row deletion and its Registry DELETE
event commit together. All original classes/packages are retained; these local
mapper/repository/data-manager methods are an explicit safety deviation pending
an equivalent upstream source change.
