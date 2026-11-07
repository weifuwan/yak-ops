package io.yak.ops.api.dto.datasync;

import lombok.Data;

@Data
public class DataSyncPublishRequest {

    private Integer version;
    private Integer expectedRevision;
}
