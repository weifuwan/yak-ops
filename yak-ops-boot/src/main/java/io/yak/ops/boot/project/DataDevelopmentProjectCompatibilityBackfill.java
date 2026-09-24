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
 * Moves legacy global Data Development rows into a concrete Project Space before PROJECT_REQUIRED
 * becomes the permanent HTTP contract.
 */
@Component
@ConditionalOnClass(name = "io.yak.ops.business.development.config.DataDevelopmentPersistenceConfiguration")
@DependsOn("dataDevelopmentFlyway")
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DataDevelopmentProjectCompatibilityBackfill {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DataDevelopmentProjectCompatibilityBackfill.class);

  private final ProjectCompatibilityCoordinator projectCoordinator;
  private final JdbcTemplate jdbcTemplate;

  public DataDevelopmentProjectCompatibilityBackfill(
      ProjectCompatibilityCoordinator projectCoordinator,
      @Qualifier("yakBusinessDataSource") DataSource dataSource) {
    this.projectCoordinator = projectCoordinator;
    this.jdbcTemplate = new JdbcTemplate(dataSource);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void backfillLegacyRows() {
    long defaultProjectId = projectCoordinator.ensureRequiredDefaultProject();
    failOnAmbiguousLegacyDirectories();

    int inferredDirectories = jdbcTemplate.update(
        "UPDATE yak_dev_directory d "
            + "JOIN ("
            + "  SELECT directory_id, MIN(project_id) AS project_id "
            + "  FROM yak_dev_node "
            + "  WHERE directory_id IS NOT NULL AND directory_id > 0 AND project_id IS NOT NULL "
            + "  GROUP BY directory_id HAVING COUNT(DISTINCT project_id) = 1"
            + ") scoped_owner ON scoped_owner.directory_id = d.id "
            + "SET d.project_id = scoped_owner.project_id WHERE d.project_id IS NULL");
    int defaultDirectories = jdbcTemplate.update(
        "UPDATE yak_dev_directory SET project_id = ? WHERE project_id IS NULL",
        defaultProjectId);
    int inferredNodes = jdbcTemplate.update(
        "UPDATE yak_dev_node n JOIN yak_dev_directory d ON d.id = n.directory_id "
            + "SET n.project_id = d.project_id "
            + "WHERE n.project_id IS NULL AND n.directory_id > 0 AND d.project_id IS NOT NULL");
    int defaultNodes = jdbcTemplate.update(
        "UPDATE yak_dev_node SET project_id = ? WHERE project_id IS NULL",
        defaultProjectId);
    int executions = jdbcTemplate.update(
        "UPDATE yak_dev_task_execution e JOIN yak_dev_node n ON n.id = e.node_id "
            + "SET e.project_id = n.project_id WHERE e.project_id IS NULL AND n.project_id IS NOT NULL");
    int lineage = jdbcTemplate.update(
        "UPDATE yak_dev_lineage_outbox o JOIN yak_dev_node n ON n.id = o.node_id "
            + "SET o.project_id = n.project_id WHERE o.project_id IS NULL AND n.project_id IS NOT NULL");

    assertNoUnscopedRows("yak_dev_directory");
    assertNoUnscopedRows("yak_dev_node");
    assertNoUnscopedRows("yak_dev_task_execution");
    assertNoUnscopedRows("yak_dev_lineage_outbox");

    LOGGER.info(
        "Data Development Project Space backfill complete: defaultProjectId={}, inferredDirectories={}, defaultDirectories={}, inferredNodes={}, defaultNodes={}, executions={}, lineage={}",
        defaultProjectId,
        inferredDirectories,
        defaultDirectories,
        inferredNodes,
        defaultNodes,
        executions,
        lineage);
  }

  private void failOnAmbiguousLegacyDirectories() {
    Long ambiguous = jdbcTemplate.queryForObject(
        "SELECT COUNT(1) FROM ("
            + "  SELECT directory_id FROM yak_dev_node "
            + "  WHERE directory_id IS NOT NULL AND directory_id > 0 AND project_id IS NOT NULL "
            + "  GROUP BY directory_id HAVING COUNT(DISTINCT project_id) > 1"
            + ") conflicts",
        Long.class);
    if (ambiguous != null && ambiguous > 0L) {
      throw new IllegalStateException(
          "Data Development Project Space cutover found "
              + ambiguous
              + " legacy directories referenced by nodes from multiple projects. Resolve those directory ownership conflicts before startup.");
    }
  }

  private void assertNoUnscopedRows(String table) {
    Long count = jdbcTemplate.queryForObject(
        "SELECT COUNT(1) FROM " + table + " WHERE project_id IS NULL", Long.class);
    if (count != null && count > 0L) {
      throw new IllegalStateException(
          "Data Development Project Space cutover left " + count + " unscoped rows in " + table);
    }
  }
}
