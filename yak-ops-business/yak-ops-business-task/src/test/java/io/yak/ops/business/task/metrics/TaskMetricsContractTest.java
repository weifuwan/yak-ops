package io.yak.ops.business.task.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.task.metrics.impl.MetricsServiceImpl;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.repository.task.MetricsRepository;
import io.yak.ops.dao.repository.task.MetricsStats;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Task指标根实例聚合、Workspace隔离、时间范围与空值归零合同。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
class TaskMetricsContractTest {

    @AfterEach
    void clearWorkspace() {
        WorkspaceContext.clear();
    }

    @Test
    void shouldQueryOnlyRootInstancesAndNormalizeTypeFilter() throws Exception {
        WorkspaceContext.bind("workspace-1");
        MetricsRepository repository = mock(MetricsRepository.class);
        MetricsServiceImpl service = new MetricsServiceImpl();
        Field field = MetricsServiceImpl.class.getDeclaredField("metricsRepository");
        field.setAccessible(true);
        field.set(service, repository);
        MetricsStats stats = new MetricsStats();
        stats.setInstanceCount(3L);
        stats.setSucceededCount(2L);
        when(repository.querySummary(eq("workspace-1"), eq("DATA_SYNC"), any(), any())).thenReturn(stats);

        var result = service.querySummary("data-sync", 7);
        assertEquals("DATA_SYNC", result.getTaskType());
        assertEquals(3L, result.getInstanceCount());
        assertEquals(2L, result.getSucceededCount());
        assertEquals(0L, result.getFailedCount());
        assertEquals(0L, result.getActiveCount());
        verify(repository).querySummary(eq("workspace-1"), eq("DATA_SYNC"), any(), any());
    }

    @Test
    void shouldRejectUnboundedMetricRange() {
        WorkspaceContext.bind("workspace-1");
        MetricsServiceImpl service = new MetricsServiceImpl();
        assertThrows(BusinessException.class, () -> service.querySummary(null, 0));
        assertThrows(BusinessException.class, () -> service.querySummary(null, 32));
    }
}
