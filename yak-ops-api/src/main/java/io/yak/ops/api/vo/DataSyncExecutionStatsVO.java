package io.yak.ops.api.vo;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataSyncExecutionStatsVO {

    private String startDate;
    private String endDate;
    private List<Daily> days;

    @Data
    @Builder
    public static class Daily {

        private String date;
        private String syncType;
        private long executionCount;
        private long succeededCount;
        private long failedCount;
        private long stoppedCount;
        private long runningCount;
        private long queuedCount;
        private long pausedCount;
    }
}
