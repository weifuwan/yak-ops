package io.yak.ops.business.sync.offline.execution.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.offline.cursor.OfflineCursorGateway;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class OfflineBatchScopeExecutionAdapterTest {

  private final OfflineCursorGateway cursorGateway = mock(OfflineCursorGateway.class);
  private final OfflineBatchScopeExecutionAdapter adapter =
      new OfflineBatchScopeExecutionAdapter(new ObjectMapper(), cursorGateway);

  @Test
  void dataWindowProjectsToWhereConditionWithoutChangingDomainScope() {
    String scoped =
        adapter.apply(
            10L,
            logicalJobSpec("tenant_id = 7"),
            BatchScope.dataWindow(
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 2, 0, 0)));
    assertThat(scoped).contains("tenant_id = 7");
    assertThat(scoped).contains("updated_at >= '2026-08-01 00:00'");
    assertThat(scoped).contains("updated_at < '2026-08-02 00:00'");
  }

  @Test
  void cursorRangeUsesCursorRouteInsteadOfTreatingCursorIdAsColumn() {
    when(cursorGateway.requireSourceColumn(10L, "orders-watermark")).thenReturn("updated_at");
    String scoped =
        adapter.apply(
            10L,
            logicalJobSpec(null),
            BatchScope.cursorRange("orders-watermark", "100", "200"));
    assertThat(scoped).contains("updated_at > '100'");
    assertThat(scoped).contains("updated_at <= '200'");
  }

  @Test
  void regularIncrementalRangeUsesItsFrozenColumnAndBounds() {
    String scoped =
        adapter.apply(
            10L,
            logicalJobSpec(null),
            BatchScope.incrementalRange(
                "timestamp-incremental", "updated_at", "route", "100", "200"));
    assertThat(scoped).contains("updated_at > '100'");
    assertThat(scoped).contains("updated_at <= '200'");
  }

  @Test
  void bootstrapKeepsTheOriginalFullSelectionJobSpec() {
    String logical = logicalJobSpec(null);
    assertThat(
            adapter.apply(
                10L,
                logical,
                BatchScope.incrementalBootstrap(
                    "timestamp-incremental", "updated_at", "route", "200")))
        .isEqualTo(logical);
  }

  @Test
  void scopedBatchRejectsMultiTableJobSpecInsteadOfGuessingRoute() {
    String logical =
        "{\"source\":{\"connectorId\":\"jdbc\",\"options\":{\"table_list\":[{\"table_path\":\"a\"},{\"table_path\":\"b\"}],\"partition_column\":\"updated_at\"}},\"sink\":{}}";
    assertThatThrownBy(
            () -> adapter.validate(10L, logical, BatchScope.partitions(java.util.List.of("p1"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("单表");
  }

  @Test
  void scopedBatchRejectsFanOutEnvelopeBeforeTryingToInterpretOneChildRoute() {
    String logical =
        "{\"apiVersion\":\"yak-ops/v1\",\"kind\":\"OfflineExecutionPlan\",\"strategy\":\"FAN_OUT\",\"units\":[]}";

    assertThatThrownBy(
            () ->
                adapter.validate(
                    10L,
                    logical,
                    BatchScope.dataWindow(
                        LocalDateTime.of(2026, 8, 1, 0, 0),
                        LocalDateTime.of(2026, 8, 2, 0, 0))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("FAN_OUT")
        .hasMessageContaining("FULL_SELECTION");
  }

  private String logicalJobSpec(String whereCondition) {
    String where =
        whereCondition == null
            ? ""
            : ",\"where_condition\":\"" + whereCondition + "\"";
    return "{\"kind\":\"BatchSyncJob\",\"source\":{\"connectorId\":\"jdbc\",\"options\":{\"table_path\":\"orders\",\"partition_column\":\"updated_at\""
        + where
        + "}},\"sink\":{}}";
  }
}
