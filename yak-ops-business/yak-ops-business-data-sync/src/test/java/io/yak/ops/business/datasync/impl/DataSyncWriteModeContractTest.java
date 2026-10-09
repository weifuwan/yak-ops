package io.yak.ops.business.datasync.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.common.bean.dto.datasync.DataSyncTaskDTO;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import org.junit.jupiter.api.Test;

class DataSyncWriteModeContractTest {

    @Test
    void shouldDefaultTaskWriteModeToAppend() {
        assertEquals(DataSyncWriteMode.APPEND, new DataSyncTaskDTO().getWriteMode());
    }

    @Test
    void shouldKeepStablePersistenceValues() {
        assertEquals(1, DataSyncWriteMode.APPEND.getValue());
        assertEquals(2, DataSyncWriteMode.OVERWRITE.getValue());
        assertEquals(3, DataSyncWriteMode.UPSERT.getValue());
    }
}
