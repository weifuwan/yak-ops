package io.yak.ops.boot.project;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
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
 * Completes the Dataset Project Space cutover after the nullable project expand migration.
 *
 * <p>Dataset is the Project Root. Version and Field rows inherit ownership through their parent
 * Dataset and therefore do not receive duplicate project columns. Historical Dataset ownership is
 * inferred from trusted producers before the compatibility default Project is used.
 */
@Component
@ConditionalOnClass(name = "io.yak.ops.business.dataset.config.DatasetPersistenceConfiguration")
@DependsOn("yakDatasetFlyway")
@ConditionalOnDataSourceEnabled
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DatasetProjectCompatibilityBackfill {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DatasetProjectCompatibilityBackfill.class);
  private static final String DATA_DEVELOPMENT_SOURCE = "DATA_DEVELOPMENT";

  private final ProjectCompatibilityCoordinator projectCoordinator;
  private final DataSourceProjectCompatibilityBackfill dataSourceBackfill;
  private final DataDevelopmentProjectCompatibilityBackfill dataDevelopmentBackfill;
  private final JdbcTemplate jdbcTemplate;

  public DatasetProjectCompatibilityBackfill(
      ProjectCompatibilityCoordinator projectCoordinator,
      DataSourceProjectCompatibilityBackfill dataSourceBackfill,
      DataDevelopmentProjectCompatibilityBackfill dataDevelopmentBackfill,
      @Qualifier("yakBusinessDataSource") DataSource dataSource) {
    this.projectCoordinator = projectCoordinator;
    this.dataSourceBackfill = dataSourceBackfill;
    this.dataDevelopmentBackfill = dataDevelopmentBackfill;
    this.jdbcTemplate = new JdbcTemplate(dataSource);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void backfillLegacyRows() {
    // Dataset ownership may be inferred from either source. Both backfills are idempotent and are
    // called explicitly here to remove ApplicationReady listener-order races.
    dataSourceBackfill.backfillLegacyRows();
    dataDevelopmentBackfill.backfillLegacyRows();

    // Preflight includes the producer-derived Project of legacy Data Development TaskAssets before
    // any Dataset-owned projection row is mutated. A conflict therefore fails without partial
    // Dataset/TaskAsset cutover writes.
    failOnAmbiguousSourceOwnership();

    // The projection rollout intentionally allowed nullable TaskAsset ownership until the producer
    // became Project-aware. Dataset cannot guess that ownership, but a referenced DATA_DEVELOPMENT
    // asset can be claimed safely from its producer DevelopmentNode source truth.
    int claimedTaskAssets = claimReferencedDataDevelopmentTaskAssets();
    assertNoUnscopedReferencedTaskAssets();

    long defaultProjectId = projectCoordinator.ensureRequiredDefaultProject();

    int fromDevelopmentNode = jdbcTemplate.update(
        "UPDATE yak_dataset d "
            + "JOIN yak_dev_node n ON n.id = d.development_node_id "
            + "SET d.project_id = n.project_id "
            + "WHERE d.project_id IS NULL AND n.project_id IS NOT NULL");

    int fromTaskAsset = jdbcTemplate.update(
        "UPDATE yak_dataset d "
            + "JOIN yak_dataset_version v ON v.dataset_id = d.id AND v.source_task_asset_id > 0 "
            + "JOIN yak_task_asset a ON a.id = v.source_task_asset_id "
            + "SET d.project_id = a.project_id "
            + "WHERE d.project_id IS NULL AND a.project_id IS NOT NULL");

    int fromDataSource = jdbcTemplate.update(
        "UPDATE yak_dataset d "
            + "JOIN yak_dataset_version v ON v.dataset_id = d.id "
            + "AND v.data_source_id REGEXP '^[0-9]+$' "
            + "JOIN yak_ops_data_source s ON s.id = CAST(v.data_source_id AS UNSIGNED) "
            + "SET d.project_id = s.project_id "
            + "WHERE d.project_id IS NULL AND s.project_id IS NOT NULL");

    int defaultDatasets = jdbcTemplate.update(
        "UPDATE yak_dataset SET project_id = ? WHERE project_id IS NULL",
        defaultProjectId);

    int inheritedDiagnostics = jdbcTemplate.update(
        "UPDATE yak_dataset_query_performance q "
            + "JOIN yak_dataset d ON d.id = q.dataset_id "
            + "SET q.project_id = d.project_id "
            + "WHERE q.project_id IS NULL AND d.project_id IS NOT NULL");
    int defaultDiagnostics = jdbcTemplate.update(
        "UPDATE yak_dataset_query_performance SET project_id = ? WHERE project_id IS NULL",
        defaultProjectId);

    assertNoUnscopedRows("yak_dataset");
    assertNoUnscopedRows("yak_dataset_query_performance");
    assertNoSourceOwnershipMismatch();
    assertNoDiagnosticOwnershipMismatch();

    LOGGER.info(
        "Dataset Project Space backfill complete: defaultProjectId={}, claimedTaskAssets={}, fromDevelopmentNode={}, fromTaskAsset={}, fromDataSource={}, defaultDatasets={}, inheritedDiagnostics={}, defaultDiagnostics={}",
        defaultProjectId,
        claimedTaskAssets,
        fromDevelopmentNode,
        fromTaskAsset,
        fromDataSource,
        defaultDatasets,
        inheritedDiagnostics,
        defaultDiagnostics);
  }

  private int claimReferencedDataDevelopmentTaskAssets() {
    Long conflicts = jdbcTemplate.queryForObject(
        "SELECT COUNT(1) FROM yak_task_asset legacy "
            + "JOIN (SELECT DISTINCT source_task_asset_id FROM yak_dataset_version "
            + "WHERE source_task_asset_id > 0) referenced ON referenced.source_task_asset_id = legacy.id "
            + "JOIN yak_dev_node n ON legacy.source = ? "
            + "AND legacy.source_ref REGEXP '^[0-9]+$' "
            + "AND n.id = CAST(legacy.source_ref AS UNSIGNED) "
            + "JOIN yak_task_asset scoped ON scoped.source = legacy.source "
            + "AND scoped.source_ref = legacy.source_ref "
            + "AND scoped.project_id = n.project_id AND scoped.id <> legacy.id "
            + "WHERE legacy.project_id IS NULL AND n.project_id IS NOT NULL",
        Long.class,
        DATA_DEVELOPMENT_SOURCE);
    if (conflicts != null && conflicts > 0L) {
      throw new IllegalStateException(
          "Dataset Project Space cutover found "
              + conflicts
              + " referenced legacy TaskAsset rows that already have a different scoped projection identity. Resolve or republish these Data Development assets before startup.");
    }

    return jdbcTemplate.update(
        "UPDATE yak_task_asset a "
            + "JOIN (SELECT DISTINCT source_task_asset_id FROM yak_dataset_version "
            + "WHERE source_task_asset_id > 0) referenced ON referenced.source_task_asset_id = a.id "
            + "JOIN yak_dev_node n ON a.source = ? "
            + "AND a.source_ref REGEXP '^[0-9]+$' "
            + "AND n.id = CAST(a.source_ref AS UNSIGNED) "
            + "SET a.project_id = n.project_id, a.update_time = NOW(6) "
            + "WHERE a.project_id IS NULL AND n.project_id IS NOT NULL",
        DATA_DEVELOPMENT_SOURCE);
  }

  private void assertNoUnscopedReferencedTaskAssets() {
    assertNoRows(
        "SELECT COUNT(DISTINCT a.id) FROM yak_dataset_version v "
            + "JOIN yak_task_asset a ON a.id = v.source_task_asset_id "
            + "WHERE v.source_task_asset_id > 0 AND a.project_id IS NULL",
        "Dataset-referenced TaskAsset rows without Project ownership; republish or migrate the owning producer first");
  }

  private void failOnAmbiguousSourceOwnership() {
    Long conflicts = jdbcTemplate.queryForObject(
        "SELECT COUNT(1) FROM ("
            + "SELECT candidate.dataset_id FROM ("
            + "SELECT d.id AS dataset_id, d.project_id AS project_id FROM yak_dataset d "
            + "WHERE d.project_id IS NOT NULL "
            + "UNION ALL "
            + "SELECT d.id, n.project_id FROM yak_dataset d "
            + "JOIN yak_dev_node n ON n.id = d.development_node_id "
            + "WHERE n.project_id IS NOT NULL "
            + "UNION ALL "
            + "SELECT v.dataset_id, a.project_id FROM yak_dataset_version v "
            + "JOIN yak_task_asset a ON a.id = v.source_task_asset_id "
            + "WHERE v.source_task_asset_id > 0 AND a.project_id IS NOT NULL "
            + "UNION ALL "
            + "SELECT v.dataset_id, n.project_id FROM yak_dataset_version v "
            + "JOIN yak_task_asset a ON a.id = v.source_task_asset_id "
            + "JOIN yak_dev_node n ON a.source = ? "
            + "AND a.source_ref REGEXP '^[0-9]+$' "
            + "AND n.id = CAST(a.source_ref AS UNSIGNED) "
            + "WHERE v.source_task_asset_id > 0 AND a.project_id IS NULL "
            + "AND n.project_id IS NOT NULL "
            + "UNION ALL "
            + "SELECT v.dataset_id, s.project_id FROM yak_dataset_version v "
            + "JOIN yak_ops_data_source s ON v.data_source_id REGEXP '^[0-9]+$' "
            + "AND s.id = CAST(v.data_source_id AS UNSIGNED) "
            + "WHERE s.project_id IS NOT NULL"
            + ") candidate GROUP BY candidate.dataset_id "
            + "HAVING COUNT(DISTINCT candidate.project_id) > 1"
            + ") conflicts",
        Long.class,
        DATA_DEVELOPMENT_SOURCE);
    if (conflicts != null && conflicts > 0L) {
      throw new IllegalStateException(
          "Dataset Project Space cutover found "
              + conflicts
              + " Dataset rows whose trusted sources disagree on Project ownership. Resolve these conflicts before startup.");
    }
  }

  private void assertNoSourceOwnershipMismatch() {
    assertNoRows(
        "SELECT COUNT(1) FROM yak_dataset d "
            + "JOIN yak_dev_node n ON n.id = d.development_node_id "
            + "WHERE n.project_id IS NOT NULL AND d.project_id <> n.project_id",
        "Dataset / Data Development node Project mismatch");
    assertNoRows(
        "SELECT COUNT(1) FROM yak_dataset d "
            + "JOIN yak_dataset_version v ON v.dataset_id = d.id AND v.source_task_asset_id > 0 "
            + "JOIN yak_task_asset a ON a.id = v.source_task_asset_id "
            + "WHERE a.project_id IS NOT NULL AND d.project_id <> a.project_id",
        "Dataset / TaskAsset Project mismatch");
    assertNoRows(
        "SELECT COUNT(1) FROM yak_dataset d "
            + "JOIN yak_dataset_version v ON v.dataset_id = d.id "
            + "AND v.data_source_id REGEXP '^[0-9]+$' "
            + "JOIN yak_ops_data_source s ON s.id = CAST(v.data_source_id AS UNSIGNED) "
            + "WHERE s.project_id IS NOT NULL AND d.project_id <> s.project_id",
        "Dataset / DataSource Project mismatch");
  }

  private void assertNoDiagnosticOwnershipMismatch() {
    assertNoRows(
        "SELECT COUNT(1) FROM yak_dataset_query_performance q "
            + "JOIN yak_dataset d ON d.id = q.dataset_id "
            + "WHERE q.project_id <> d.project_id",
        "Dataset query diagnostics Project mismatch");
  }

  private void assertNoUnscopedRows(String table) {
    assertNoRows(
        "SELECT COUNT(1) FROM " + table + " WHERE project_id IS NULL",
        "unscoped rows in " + table);
  }

  private void assertNoRows(String sql, String message) {
    Long count = jdbcTemplate.queryForObject(sql, Long.class);
    if (count != null && count > 0L) {
      throw new IllegalStateException(
          "Dataset Project Space cutover found " + count + " " + message);
    }
  }
}
