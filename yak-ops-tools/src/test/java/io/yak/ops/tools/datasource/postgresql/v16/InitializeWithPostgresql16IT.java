package io.yak.ops.tools.datasource.postgresql.v16;

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
@YakOpsDatabaseContainer(imageName = "postgres:16.0")
class InitializeWithPostgresql16IT {

    @Autowired
    private YakOpsManager yakOpsManager;

    @Test
    @DisplayName("Test initialize Yak Ops database in PostgreSQL")
    void testInitializeWithPostgreSQLProfile() {
        Assertions.assertDoesNotThrow(() -> {
            yakOpsManager.initializeOrValidate();
        });
        // todo: Assert table count
    }
}
