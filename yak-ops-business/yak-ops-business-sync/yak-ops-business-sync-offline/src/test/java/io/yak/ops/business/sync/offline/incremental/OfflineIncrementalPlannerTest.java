package io.yak.ops.business.sync.offline.incremental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.datasource.domain.catalog.CatalogQueryResult;
import io.yak.ops.business.datasource.domain.catalog.CatalogReadRequest;
import io.yak.ops.business.sync.offline.cursor.OfflineCursorGateway;
import io.yak.ops.business.sync.offline.domain.OfflineSyncCursor;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import java.sql.Types;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfflineIncrementalPlannerTest {

  @Mock private DataSourceCatalogReader catalogReader;
  @Mock private OfflineCursorGateway cursorGateway;
  private OfflineIncrementalPlanner planner;

  @BeforeEach
  void setUp() {
    planner = new OfflineIncrementalPlanner(new ObjectMapper(), catalogReader, cursorGateway);
    org.mockito.Mockito.lenient()
        .when(catalogReader.listColumn(eq(1L), any(CatalogReadRequest.class)))
        .thenReturn(
            List.of(
                new CatalogColumn(
                    "updated_at", "TIMESTAMP", Types.TIMESTAMP, null, null, false, 1, false, null)));
  }

  @Test
  void firstRunFreezesUpperBoundForFullBootstrap() {
    when(cursorGateway.find(10L, OfflineIncrementalPlanner.CURSOR_ID)).thenReturn(Optional.empty());
    upper("2026-09-12 10:00:00");

    BatchScope scope = planner.plan(10L, definition());

    assertThat(scope).isInstanceOf(BatchScope.IncrementalRange.class);
    BatchScope.IncrementalRange range = (BatchScope.IncrementalRange) scope;
    assertThat(range.bootstrapFull()).isTrue();
    assertThat(range.throughInclusive()).isEqualTo("2026-09-12 10:00:00");
  }

  @Test
  void subsequentRunUsesCommittedCursorAndCapturedUpperBound() {
    when(cursorGateway.find(10L, OfflineIncrementalPlanner.CURSOR_ID))
        .thenReturn(Optional.of(cursor("2026-09-12 09:00:00")));
    upper("2026-09-12 10:00:00");

    BatchScope.IncrementalRange range =
        (BatchScope.IncrementalRange) planner.plan(10L, definition());

    assertThat(range.bootstrapFull()).isFalse();
    assertThat(range.afterExclusive()).isEqualTo("2026-09-12 09:00:00");
    assertThat(range.throughInclusive()).isEqualTo("2026-09-12 10:00:00");
  }

  @Test
  void unchangedMaxCreatesLocalZeroRowBatch() {
    when(cursorGateway.find(10L, OfflineIncrementalPlanner.CURSOR_ID))
        .thenReturn(Optional.of(cursor("2026-09-12 10:00:00")));
    upper("2026-09-12 10:00:00");
    assertThat(planner.plan(10L, definition())).isInstanceOf(BatchScope.EmptySelection.class);
  }

  @Test
  void nullMaxCreatesLocalZeroRowBatchWithoutInitializingCursor() {
    when(cursorGateway.find(10L, OfflineIncrementalPlanner.CURSOR_ID)).thenReturn(Optional.empty());
    when(catalogReader.preview(eq(1L), any(CatalogReadRequest.class)))
        .thenReturn(
            new CatalogQueryResult(
                List.of(),
                List.of(Collections.singletonMap("yak_incremental_upper", null)),
                1));
    assertThat(planner.plan(10L, definition())).isInstanceOf(BatchScope.EmptySelection.class);
  }

  @Test
  void lowerSourceMaxIsRejected() {
    when(cursorGateway.find(10L, OfflineIncrementalPlanner.CURSOR_ID))
        .thenReturn(Optional.of(cursor("2026-09-12 10:00:00")));
    upper("2026-09-12 09:00:00");

    assertThatThrownBy(() -> planner.plan(10L, definition()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("拒绝回退");
  }

  @Test
  void nonUpsertTargetIsRejectedBeforeReadingMetadata() {
    assertThatThrownBy(() -> planner.plan(10L, definition().replace("upsert", "append")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("UPSERT");
  }

  @Test
  void changedSourceRouteCannotReuseCommittedCursor() {
    OfflineSyncCursor cursor =
        new OfflineSyncCursor(
            10L,
            OfflineIncrementalPlanner.CURSOR_ID,
            "updated_at",
            "different-route-signature",
            "2026-09-12 09:00:00",
            7L,
            1L);
    when(cursorGateway.find(10L, OfflineIncrementalPlanner.CURSOR_ID))
        .thenReturn(Optional.of(cursor));

    assertThatThrownBy(() -> planner.plan(10L, definition()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("来源路由不同");
  }

  private OfflineSyncCursor cursor(String position) {
    return new OfflineSyncCursor(
        10L, OfflineIncrementalPlanner.CURSOR_ID, "updated_at", position, 7L, 1L);
  }

  private void upper(String value) {
    when(catalogReader.preview(eq(1L), any(CatalogReadRequest.class)))
        .thenReturn(
            new CatalogQueryResult(
                List.of(), List.of(Map.of("yak_incremental_upper", value)), 1));
  }

  private String definition() {
    return """
        {
          "basic":{"jobName":"orders","mode":"GUIDE_SINGLE"},
          "source":{"connectorId":"jdbc","dbType":"MYSQL","dataSourceId":"1","config":{"readMode":"table","table":"public.orders"}},
          "sink":{"connectorId":"jdbc","dataSourceId":"2","config":{"writeMode":"upsert","primaryKey":"id","table":"orders"}},
          "incremental":{"enabled":true,"strategy":"MAX_TIMESTAMP","column":"updated_at","bootstrapMode":"SOURCE_CURRENT_MAX"}
        }
        """;
  }
}
