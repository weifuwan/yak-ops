package io.yak.ops.tools.datasource.mysql.v8;

import io.yak.ops.tools.datasource.DolphinSchedulerManager;
import io.yak.ops.tools.datasource.jupiter.DolphinSchedulerDatabaseContainer;
import io.yak.ops.tools.datasource.mysql.DolphinSchedulerMysqlProfile;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@DolphinSchedulerMysqlProfile
@DolphinSchedulerDatabaseContainer(imageName = "mysql:8.0")
class InitializeWithMysqlIT {

    @Autowired
    private DolphinSchedulerManager dolphinSchedulerManager;

    @Test
    @DisplayName("Test Initialize DolphinScheduler database in MySQL")
    void testInitializeWithMysqlProfile() {
        Assertions.assertDoesNotThrow(() -> dolphinSchedulerManager.initDolphinScheduler());
        // todo: Assert table count

    }

}
