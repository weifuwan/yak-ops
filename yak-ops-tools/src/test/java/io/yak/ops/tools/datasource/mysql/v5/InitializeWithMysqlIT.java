package io.yak.ops.tools.datasource.mysql.v5;

import io.yak.ops.tools.datasource.YakOpsManager;
import io.yak.ops.tools.datasource.jupiter.YakOpsDatabaseContainer;
import io.yak.ops.tools.datasource.mysql.YakOpsMysqlProfile;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.beans.factory.annotation.Autowired;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@EnabledOnOs(OS.LINUX)
@YakOpsMysqlProfile
@YakOpsDatabaseContainer(imageName = "mysql:5.7")
class InitializeWithMysqlIT {

    @Autowired
    private YakOpsManager yakOpsManager;

    @Test
    @DisplayName("Test Initialize YakOps database in MySQL")
    void testInitializeWithMysqlProfile() {
        Assertions.assertDoesNotThrow(() -> yakOpsManager.initializeOrValidate());
        // todo: Assert table count

    }

}
