package io.yak.ops.boot.project;

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Completes the Resource Project Space cutover after the existing V2 expand migration. */
@Component
@ConditionalOnClass(name = "io.yak.ops.business.resource.config.ResourceConfiguration")
@DependsOn("opsResourceFlyway")
@ConditionalOnExpression("${yak.resource.enabled:true}")
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ResourceProjectCompatibilityBackfill {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ResourceProjectCompatibilityBackfill.class);

  private final ProjectCompatibilityCoordinator projectCoordinator;
  private final JdbcTemplate jdbcTemplate;

  public ResourceProjectCompatibilityBackfill(
      ProjectCompatibilityCoordinator projectCoordinator,
      @Qualifier("yakBusinessDataSource") DataSource dataSource) {
    this.projectCoordinator = projectCoordinator;
    this.jdbcTemplate = new JdbcTemplate(dataSource);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void backfillLegacyRows() {
    long defaultProjectId = projectCoordinator.ensureRequiredDefaultProject();

    int inheritedRows = inheritProjectFromParents();
    int defaultRows =
        jdbcTemplate.update(
            "UPDATE yak_ops_resource SET project_id = ? WHERE project_id IS NULL",
            defaultProjectId);

    assertNoUnscopedRows();
    assertNoCrossProjectParentLinks();

    LOGGER.info(
        "Resource Project Space backfill complete: defaultProjectId={}, inheritedRows={}, defaultRows={}",
        defaultProjectId,
        inheritedRows,
        defaultRows);
  }

  private int inheritProjectFromParents() {
    int total = 0;
    int updated;
    do {
      updated =
          jdbcTemplate.update(
              "UPDATE yak_ops_resource child "
                  + "JOIN yak_ops_resource parent ON child.parent_id = parent.id "
                  + "SET child.project_id = parent.project_id "
                  + "WHERE child.project_id IS NULL AND parent.project_id IS NOT NULL");
      total += updated;
    } while (updated > 0);
    return total;
  }

  private void assertNoUnscopedRows() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM yak_ops_resource WHERE project_id IS NULL", Long.class);
    if (count != null && count > 0L) {
      throw new IllegalStateException(
          "Resource Project Space cutover left " + count + " unscoped resource rows");
    }
  }

  private void assertNoCrossProjectParentLinks() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM yak_ops_resource child "
                + "JOIN yak_ops_resource parent ON child.parent_id = parent.id "
                + "WHERE child.parent_id <> 0 AND child.project_id <> parent.project_id",
            Long.class);
    if (count != null && count > 0L) {
      throw new IllegalStateException(
          "Resource Project Space cutover found "
              + count
              + " cross-project parent-child resource links");
    }
  }
}
