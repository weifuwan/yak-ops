package io.yak.ops.extract.master.transportor.datasync;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSyncTaskLifecycleEventRequest {

    private Integer taskInstanceId;

    private String eventType;

    private String host;

    private Long startTime;

    private Long endTime;

    private String logPath;

    private String executePath;

    private String appIds;
}
