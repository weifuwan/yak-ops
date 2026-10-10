package io.yak.ops.business.datasync.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.bean.dto.datasync.DataSyncOperationsDashboardDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsDashboardVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncOperationsTrendPointVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.enums.datasync.DataSyncOperationsRange;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsFailureStats;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsMetricsRepository;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsStatusStats;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsSummaryStats;
import io.yak.ops.dao.repository.datasync.DataSyncOperationsTrendStats;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DataSyncOperationsMetricsReadModelTest {

    @AfterEach
    void clearWorkspace() {
        WorkspaceContext.clear();
    }

    @Test
    void shouldBuildWorkspaceScopedSevenDayDashboardAndFillMissingDailyBuckets() throws Exception {
        DataSyncOperationsServiceImpl service = new DataSyncOperationsServiceImpl();
        AtomicReference<LocalDateTime> capturedStart = new AtomicReference<>();
        AtomicReference<LocalDateTime> capturedEnd = new AtomicReference<>();
        inject(service, "operationsMetricsRepository", repository(capturedStart, capturedEnd));

        DataSyncOperationsDashboardDTO dto = new DataSyncOperationsDashboardDTO();
        dto.setSyncType(DataSyncType.OFFLINE);
        dto.setRange(DataSyncOperationsRange.LAST_7_DAYS);

        WorkspaceContext.bind("workspace-1");
        DataSyncOperationsDashboardVO result = service.queryOperationsDashboard(dto);

        assertEquals(DataSyncType.OFFLINE.name(), result.getSyncType());
        assertEquals(DataSyncOperationsRange.LAST_7_DAYS.name(), result.getRange());
        assertEquals(capturedStart.get(), result.getRangeStart());
        assertEquals(capturedEnd.get(), result.getRangeEnd());
        assertEquals(8L, result.getSummary().getExecutionCount());
        assertEquals(2L, result.getSummary().getCurrentActiveTaskCount());
        assertEquals(1200L, result.getSummary().getWriteRows());
        assertEquals(2500L, result.getSummary().getAverageDurationMillis());

        assertEquals(7, result.getTrend().size());
        assertEquals(DataSyncInstanceStatus.values().length, result.getStatusDistribution().size());
        assertEquals(
                6L,
                result.getStatusDistribution().stream()
                        .filter(item -> DataSyncInstanceStatus.SUCCEEDED.name().equals(item.getStatus()))
                        .findFirst()
                        .orElseThrow()
                        .getCount());
        assertEquals(1, result.getFailureRanking().size());

        DataSyncOperationsTrendPointVO populated = result.getTrend().stream()
                .filter(point -> point.getExecutionCount() == 3L)
                .findFirst()
                .orElseThrow();
        assertEquals(capturedStart.get().plusDays(1), populated.getBucketStart());
        assertTrue(result.getTrend().stream().anyMatch(point -> point.getExecutionCount() == 0L));
    }

    private DataSyncOperationsMetricsRepository repository(
            AtomicReference<LocalDateTime> capturedStart, AtomicReference<LocalDateTime> capturedEnd) {
        return new DataSyncOperationsMetricsRepository() {
            @Override
            public DataSyncOperationsSummaryStats querySummary(
                    String workspaceId, DataSyncType syncType, LocalDateTime startTime, LocalDateTime endTime) {
                assertEquals("workspace-1", workspaceId);
                assertEquals(DataSyncType.OFFLINE, syncType);
                assertEquals(endTime.toLocalDate().minusDays(6).atStartOfDay(), startTime);
                capturedStart.set(startTime);
                capturedEnd.set(endTime);

                DataSyncOperationsSummaryStats stats = new DataSyncOperationsSummaryStats();
                stats.setExecutionCount(8L);
                stats.setSucceededCount(6L);
                stats.setFailedCount(1L);
                stats.setLostCount(1L);
                stats.setAbnormalTaskCount(2L);
                stats.setCurrentActiveTaskCount(2L);
                stats.setAutoRecoveryCount(0L);
                stats.setReadRows(1300L);
                stats.setWriteRows(1200L);
                stats.setAverageDurationMillis(2500L);
                return stats;
            }

            @Override
            public List<DataSyncOperationsTrendStats> queryTrend(
                    String workspaceId,
                    DataSyncType syncType,
                    LocalDateTime startTime,
                    LocalDateTime endTime,
                    boolean hourly) {
                assertTrue(!hourly);
                DataSyncOperationsTrendStats stats = new DataSyncOperationsTrendStats();
                stats.setBucketStart(startTime.plusDays(1));
                stats.setExecutionCount(3L);
                stats.setSucceededCount(2L);
                stats.setFailedCount(1L);
                stats.setLostCount(0L);
                stats.setAutoRecoveryCount(0L);
                stats.setReadRows(600L);
                stats.setWriteRows(580L);
                stats.setAverageDurationMillis(2000L);
                return List.of(stats);
            }

            @Override
            public List<DataSyncOperationsStatusStats> queryStatusDistribution(
                    String workspaceId, DataSyncType syncType, LocalDateTime startTime, LocalDateTime endTime) {
                DataSyncOperationsStatusStats stats = new DataSyncOperationsStatusStats();
                stats.setStatus(DataSyncInstanceStatus.SUCCEEDED.getValue());
                stats.setCount(6L);
                return List.of(stats);
            }

            @Override
            public List<DataSyncOperationsFailureStats> queryFailureRanking(
                    String workspaceId,
                    DataSyncType syncType,
                    LocalDateTime startTime,
                    LocalDateTime endTime,
                    int limit) {
                assertEquals(5, limit);
                DataSyncOperationsFailureStats stats = new DataSyncOperationsFailureStats();
                stats.setTaskId("task-1");
                stats.setTaskName("orders-sync");
                stats.setFailedCount(2L);
                stats.setLostCount(1L);
                stats.setAbnormalCount(3L);
                stats.setLatestFailureTime(startTime.plusDays(2));
                return List.of(stats);
            }
        };
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        DataSyncTestServices.inject(target, fieldName, value);
    }
}
