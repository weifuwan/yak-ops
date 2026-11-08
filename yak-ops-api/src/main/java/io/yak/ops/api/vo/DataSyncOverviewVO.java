package io.yak.ops.api.vo;

import java.util.Date;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataSyncOverviewVO {

    private long dataSourceCount;
    private int taskCount;
    private int onlineCount;
    private int offlineCount;
    private int runningCount;
    private int queuedCount;
    private int failedCount;
    private int succeededCount;
    private int pausedCount;
    private int stoppedCount;
    private int unreadableCount;
    private List<ScopeCount> taskTypes;
    private List<RecentFailure> recentFailures;

    @Data
    @Builder
    public static class ScopeCount {

        private String scope;
        private int offline;
        private int realtime;
        private int total;
    }

    @Data
    @Builder
    public static class RecentFailure {

        private String instanceId;
        private String taskId;
        private String taskName;
        private Date failedAt;
    }
}
