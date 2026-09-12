package io.yak.ops.business.sync.offline.repository;

import static io.yak.ops.business.sync.offline.OfflineProjectTestContext.PROJECT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.offline.dao.OfflineSyncCursorDao;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.OfflineSyncCursor;
import io.yak.ops.common.bean.po.sync.offline.OfflineSyncCursorPO;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OfflineSyncCursorRepositoryAdapterTest {

  @Test
  void initializesCursorRouteAndDelegatesCasAdvance() {
    OfflineSyncCursorDao dao = mock(OfflineSyncCursorDao.class);
    OfflineSyncCursorRepositoryAdapter repository = new OfflineSyncCursorRepositoryAdapter(dao, tasks());
    when(dao.insert(any(OfflineSyncCursorPO.class))).thenReturn(true);

    OfflineSyncCursor cursor = repository.initializeIfAbsent(
        10L, "orders", "updated_at", "100");

    assertThat(cursor.position()).isEqualTo("100");
    assertThat(cursor.stateVersion()).isEqualTo(1L);
    when(dao.advance(
            org.mockito.ArgumentMatchers.eq(10L),
            org.mockito.ArgumentMatchers.eq("orders"),
            org.mockito.ArgumentMatchers.eq("100"),
            org.mockito.ArgumentMatchers.eq(1L),
            org.mockito.ArgumentMatchers.eq("200"),
            org.mockito.ArgumentMatchers.eq(77L),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(true);

    assertThat(repository.advance(cursor, "100", "200", 77L)).isTrue();
    verify(dao).advance(
        org.mockito.ArgumentMatchers.eq(10L),
        org.mockito.ArgumentMatchers.eq("orders"),
        org.mockito.ArgumentMatchers.eq("100"),
        org.mockito.ArgumentMatchers.eq(1L),
        org.mockito.ArgumentMatchers.eq("200"),
        org.mockito.ArgumentMatchers.eq(77L),
        org.mockito.ArgumentMatchers.any());
  }

  @Test
  void existingCursorCannotBeReboundToDifferentSourceColumn() {
    OfflineSyncCursorDao dao = mock(OfflineSyncCursorDao.class);
    OfflineSyncCursorPO stored = new OfflineSyncCursorPO();
    stored.setJobDefinitionId(10L);
    stored.setCursorId("orders");
    stored.setSourceColumn("updated_at");
    stored.setPositionValue("100");
    stored.setStateVersion(1L);
    when(dao.select(10L, "orders")).thenReturn(stored);
    OfflineSyncCursorRepositoryAdapter repository = new OfflineSyncCursorRepositoryAdapter(dao, tasks());

    assertThatThrownBy(
            () -> repository.initializeIfAbsent(10L, "orders", "id", "100"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sourceColumn");
  }

  private OfflineJobDefinitionRepository tasks() {
    OfflineJobDefinitionRepository repository = mock(OfflineJobDefinitionRepository.class);
    OfflineJobDefinition definition = OfflineJobDefinition.builder()
        .id(10L)
        .projectId(PROJECT_ID)
        .jobName("cursor-test")
        .build();
    when(repository.findById(10L)).thenReturn(Optional.of(definition));
    return repository;
  }
}
