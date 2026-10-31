package io.yak.ops.business.task.metrics.impl;

import io.yak.ops.business.task.metrics.DataSyncOperationsService;
import io.yak.ops.common.bean.vo.task.DataSyncOperationsVO;
import io.yak.ops.dao.repository.task.DataSyncOperationsRepository;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Builds an operations read model without loading Task Instances into application memory. */
@Service
public class DataSyncOperationsServiceImpl implements DataSyncOperationsService {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00:00");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private DataSyncOperationsRepository repository;

    @Override
    public DataSyncOperationsVO dashboard(String workspaceId, String syncType, String range) {
        String runtimeMode =
                switch (syncType) {
                    case "OFFLINE" -> "BATCH";
                    case "REALTIME" -> "STREAMING";
                    default -> throw new IllegalArgumentException("Unsupported Data Sync type");
                };
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start =
                switch (range) {
                    case "TODAY" -> end.toLocalDate().atStartOfDay();
                    case "LAST_7_DAYS" -> end.toLocalDate().minusDays(6).atStartOfDay();
                    case "LAST_30_DAYS" -> end.toLocalDate().minusDays(29).atStartOfDay();
                    default -> throw new IllegalArgumentException("Unsupported Data Sync operations range");
                };
        boolean hourly = range.equals("TODAY");
        DataSyncOperationsVO.Summary counts = repository.summary(workspaceId, runtimeMode, start, end);
        long running = repository.runningCount(workspaceId, runtimeMode);
        DataSyncOperationsVO.Summary summary = new DataSyncOperationsVO.Summary(
                counts.executionCount(),
                counts.succeededCount(),
                counts.failedCount(),
                counts.canceledCount(),
                running);
        List<DataSyncOperationsVO.TrendPoint> trend =
                completeTrend(start, end, hourly, repository.trend(workspaceId, runtimeMode, start, end, hourly));
        List<DataSyncOperationsVO.Failure> failures = repository.recentFailures(workspaceId, runtimeMode, start, end);
        return new DataSyncOperationsVO(summary, trend, failures);
    }

    private List<DataSyncOperationsVO.TrendPoint> completeTrend(
            LocalDateTime start, LocalDateTime end, boolean hourly, List<DataSyncOperationsVO.TrendPoint> persisted) {
        Map<String, DataSyncOperationsVO.TrendPoint> counts = new HashMap<>();
        for (DataSyncOperationsVO.TrendPoint point : persisted) {
            counts.put(point.bucket(), point);
        }
        DateTimeFormatter formatter = hourly ? HOUR : DAY;
        List<DataSyncOperationsVO.TrendPoint> result = new ArrayList<>();
        LocalDateTime bucket = start;
        while (!bucket.isAfter(end)) {
            String label = bucket.format(formatter);
            DataSyncOperationsVO.TrendPoint point = counts.get(label);
            if (point == null) {
                point = new DataSyncOperationsVO.TrendPoint(label, 0, 0, 0, 0);
            }
            result.add(point);
            if (hourly) {
                bucket = bucket.plusHours(1);
            } else {
                bucket = bucket.plusDays(1);
            }
        }
        return result;
    }
}
