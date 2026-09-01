package io.yak.ops.business.quality.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class QualityWorkflowTaskMigrationTest {

  @Test
  void workflowTaskMigrationAddsImmutableRevisionAndIdempotencyContracts() throws IOException {
    String sql = Files.readString(migration());

    assertThat(sql)
        .contains("CREATE TABLE IF NOT EXISTS yak_quality_monitor_revision")
        .contains("UNIQUE KEY uk_yak_quality_monitor_revision_no")
        .contains("ADD COLUMN idempotency_key VARCHAR(255) NULL")
        .contains("UNIQUE KEY uk_yak_quality_execution_idempotency")
        .doesNotContain("UPDATE yak_quality_monitor");
  }

  private Path migration() {
    Path local = Path.of(
        "src/main/resources/db/migration/yak-quality/V2__add_quality_workflow_task_contract.sql");
    if (Files.isRegularFile(local)) return local;
    Path repository = Path.of(
        "yak-ops-business",
        "yak-ops-business-quality",
        "src",
        "main",
        "resources",
        "db",
        "migration",
        "yak-quality",
        "V2__add_quality_workflow_task_contract.sql");
    assertThat(Files.isRegularFile(repository)).isTrue();
    return repository;
  }
}
