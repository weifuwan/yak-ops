package io.yak.ops.tools.datasource.postgresql.v11;

import io.yak.ops.tools.datasource.YakOpsManager;
import io.yak.ops.tools.datasource.jupiter.YakOpsDatabaseContainer;
import io.yak.ops.tools.datasource.postgresql.YakOpsPostgresqlProfile;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@YakOpsPostgresqlProfile
@YakOpsDatabaseContainer(imageName = "postgres:11.1")
class InitializeWithPostgresqlIT {

    @Autowired
    private YakOpsManager yakOpsManager;

    @Test
    @DisplayName("Test initYakOps database in PostgreSQL")
    void testInitializeWithPostgreSQLProfile() {
        Assertions.assertDoesNotThrow(() -> {
            yakOpsManager.initYakOps();
        });
        // todo: Assert table count
    }
}
