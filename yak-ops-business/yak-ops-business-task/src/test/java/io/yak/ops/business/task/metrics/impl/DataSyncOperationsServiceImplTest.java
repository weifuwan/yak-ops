package io.yak.ops.business.task.metrics.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.common.bean.vo.task.DataSyncOperationsVO;
import io.yak.ops.dao.repository.task.DataSyncOperationsRepository;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Validates period boundaries and historical runtime-mode isolation for Instance metrics. */
class DataSyncOperationsServiceImplTest {

    private final DataSyncOperationsRepository repository = mock(DataSyncOperationsRepository.class);
    private final DataSyncOperationsServiceImpl service = new DataSyncOperationsServiceImpl();

    @BeforeEach
    void configureRepository() throws ReflectiveOperationException {
        Field field = DataSyncOperationsServiceImpl.class.getDeclaredField("repository");
        field.setAccessible(true);
        field.set(service, repository);
        when(repository.summary(eq("workspace"), any(), any(), any()))
                .thenReturn(new DataSyncOperationsVO.Summary(3, 1, 1, 1, 0));
        when(repository.trend(eq("workspace"), any(), any(), any(), any(boolean.class)))
                .thenReturn(List.of());
        when(repository.recentFailures(eq("workspace"), any(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void aggregatesOfflineHistoryAndCurrentRunningIndependently() {
        when(repository.runningCount("workspace", "BATCH")).thenReturn(2L);
        DataSyncOperationsVO result = service.dashboard("workspace", "OFFLINE", "LAST_7_DAYS");

        assertEquals(3, result.summary().executionCount());
        assertEquals(1, result.summary().succeededCount());
        assertEquals(1, result.summary().failedCount());
        assertEquals(1, result.summary().canceledCount());
        assertEquals(2, result.summary().runningCount());
        assertEquals(7, result.trend().size());
        assertTrue(result.trend().stream().allMatch(point -> point.executionCount() == 0));
        verify(repository).runningCount("workspace", "BATCH");
        verify(repository).summary(eq("workspace"), eq("BATCH"), any(), any());
    }

    @Test
    void usesHourlyBucketsForRealtimeToday() {
        DataSyncOperationsVO result = service.dashboard("workspace", "REALTIME", "TODAY");
        assertTrue(result.trend().size() >= 1);
        assertTrue(result.trend().size() <= 24);
        verify(repository).trend(eq("workspace"), eq("STREAMING"), any(), any(), eq(true));
    }

    @Test
    void rejectsUnsupportedRangeAndTypeBeforeDatabaseReads() {
        assertThrows(IllegalArgumentException.class, () -> service.dashboard("workspace", "OTHER", "TODAY"));
        assertThrows(
                IllegalArgumentException.class, () -> service.dashboard("workspace", "OFFLINE", "LAST_YEAR"));
    }

    @Test
    void startsThirtyDayRangeAtTheBeginningOfItsFirstDay() {
        service.dashboard("workspace", "OFFLINE", "LAST_30_DAYS");
        ArgumentCaptor<LocalDateTime> start = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).summary(eq("workspace"), eq("BATCH"), start.capture(), any());
        assertEquals(0, start.getValue().getHour());
        assertEquals(0, start.getValue().getMinute());
    }
}
