package io.yak.ops.boot.project;

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Moves legacy global Data Service rows into a concrete Project Space before the management plane
 * becomes PROJECT_REQUIRED. The external invocation plane remains global-by-path after the cutover.
 */
@Component
@ConditionalOnClass(name = "io.yak.ops.business.dataservice.config.DataServiceFlywayConfiguration")
@DependsOn("opsDataSourceFlyway")
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DataServiceProjectCompatibilityBackfill {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DataServiceProjectCompatibilityBackfill.class);
  private static final String DATA_DEVELOPMENT_SOURCE = "DATA_DEVELOPMENT_DATA_SERVICE";

  private final ProjectCompatibilityCoordinator projectCoordinator;
  private final DataDevelopmentProjectCompatibilityBackfill dataDevelopmentBackfill;
  private final JdbcTemplate jdbcTemplate;

  public DataServiceProjectCompatibilityBackfill(
      ProjectCompatibilityCoordinator projectCoordinator,
      DataDevelopmentProjectCompatibilityBackfill dataDevelopmentBackfill,
      @Qualifier("yakBusinessDataSource") DataSource dataSource) {
    this.projectCoordinator = projectCoordinator;
    this.dataDevelopmentBackfill = dataDevelopmentBackfill;
    this.jdbcTemplate = new JdbcTemplate(dataSource);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void backfillLegacyRows() {
    // Source-managed runtime projections must observe the final authoring Project ownership. The
    // Data Development backfill is idempotent, so calling it here also removes listener-order races.
    dataDevelopmentBackfill.backfillLegacyRows();

    long defaultProjectId = projectCoordinator.ensureRequiredDefaultProject();
    failOnManagedSourceProjectMismatch();

    int inferredApis = jdbcTemplate.update(
        "UPDATE yak_ops_data_service_api api "
            + "JOIN yak_dev_node node ON api.source_type = ? "
            + "AND node.id = CAST(api.source_ref AS UNSIGNED) "
            + "SET api.project_id = node.project_id "
            + "WHERE api.project_id IS NULL AND node.project_id IS NOT NULL",
        DATA_DEVELOPMENT_SOURCE);
    int defaultApis = jdbcTemplate.update(
        "UPDATE yak_ops_data_service_api SET project_id = ? WHERE project_id IS NULL",
        defaultProjectId);
    int inheritedLogs = jdbcTemplate.update(
        "UPDATE yak_ops_data_service_call_log log "
            + "JOIN yak_ops_data_service_api api ON api.id = log.api_id "
            + "SET log.project_id = api.project_id "
            + "WHERE log.project_id IS NULL AND api.project_id IS NOT NULL");
    int defaultLogs = jdbcTemplate.update(
        "UPDATE yak_ops_data_service_call_log SET project_id = ? WHERE project_id IS NULL",
        defaultProjectId);

    assertNoUnscopedRows("yak_ops_data_service_api");
    assertNoUnscopedRows("yak_ops_data_service_call_log");

    LOGGER.info(
        "Data Service Project Space backfill complete: defaultProjectId={}, inferredApis={}, defaultApis={}, inheritedLogs={}, defaultLogs={}",
        defaultProjectId,
        inferredApis,
        defaultApis,
        inheritedLogs,
        defaultLogs);
  }

  private void failOnManagedSourceProjectMismatch() {
    Long mismatches = jdbcTemplate.queryForObject(
        "SELECT COUNT(1) FROM yak_ops_data_service_api api "
            + "JOIN yak_dev_node node ON api.source_type = ? "
            + "AND node.id = CAST(api.source_ref AS UNSIGNED) "
            + "WHERE api.project_id IS NOT NULL AND node.project_id IS NOT NULL "
            + "AND api.project_id <> node.project_id",
        Long.class,
        DATA_DEVELOPMENT_SOURCE);
    if (mismatches != null && mismatches > 0L) {
      throw new IllegalStateException(
          "Data Service Project Space cutover found "
              + mismatches
              + " source-managed APIs whose Project Space differs from the owning Data Development node. Resolve these ownership conflicts before startup.");
    }
  }

  private void assertNoUnscopedRows(String table) {
    Long count = jdbcTemplate.queryForObject(
        "SELECT COUNT(1) FROM " + table + " WHERE project_id IS NULL", Long.class);
    if (count != null && count > 0L) {
      throw new IllegalStateException(
          "Data Service Project Space cutover left " + count + " unscoped rows in " + table);
    }
  }
}
