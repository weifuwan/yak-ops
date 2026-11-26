package io.yak.ops.extract.master.transportor.datasync;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSyncTaskLifecycleEventResponse {

    private boolean success;

    private String message;

    public static DataSyncTaskLifecycleEventResponse accepted() {
        return DataSyncTaskLifecycleEventResponse.builder().success(true).build();
    }

    public static DataSyncTaskLifecycleEventResponse rejected(String message) {
        return DataSyncTaskLifecycleEventResponse.builder().success(false).message(message).build();
    }
}
