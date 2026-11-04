package io.yak.ops.tools.datasource.postgresql.v11;

import io.yak.ops.tools.datasource.DolphinSchedulerManager;
import io.yak.ops.tools.datasource.jupiter.DolphinSchedulerDatabaseContainer;
import io.yak.ops.tools.datasource.postgresql.DolphinSchedulerPostgresqlProfile;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@DolphinSchedulerPostgresqlProfile
@DolphinSchedulerDatabaseContainer(imageName = "postgres:11.1")
class InitializeWithPostgresqlIT {

    @Autowired
    private DolphinSchedulerManager dolphinSchedulerManager;

    @Test
    @DisplayName("Test initDolphinScheduler database in PostgreSQL")
    void testInitializeWithPostgreSQLProfile() {
        Assertions.assertDoesNotThrow(() -> {
            dolphinSchedulerManager.initDolphinScheduler();
        });
        // todo: Assert table count
    }
}
