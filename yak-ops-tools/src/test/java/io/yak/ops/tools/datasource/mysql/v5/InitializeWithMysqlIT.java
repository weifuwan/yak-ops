package io.yak.ops.tools.datasource.mysql.v5;

import io.yak.ops.tools.datasource.DolphinSchedulerManager;
import io.yak.ops.tools.datasource.jupiter.DolphinSchedulerDatabaseContainer;
import io.yak.ops.tools.datasource.mysql.DolphinSchedulerMysqlProfile;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.beans.factory.annotation.Autowired;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@EnabledOnOs(OS.LINUX)
@DolphinSchedulerMysqlProfile
@DolphinSchedulerDatabaseContainer(imageName = "mysql:5.7")
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
