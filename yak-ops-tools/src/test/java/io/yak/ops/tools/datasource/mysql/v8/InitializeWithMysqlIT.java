package io.yak.ops.tools.datasource.mysql.v8;

import io.yak.ops.tools.datasource.YakOpsManager;
import io.yak.ops.tools.datasource.jupiter.YakOpsDatabaseContainer;
import io.yak.ops.tools.datasource.mysql.YakOpsMysqlProfile;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@YakOpsMysqlProfile
@YakOpsDatabaseContainer(imageName = "mysql:8.0")
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
