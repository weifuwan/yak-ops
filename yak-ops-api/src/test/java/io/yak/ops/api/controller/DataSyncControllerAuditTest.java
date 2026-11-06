package io.yak.ops.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import io.yak.ops.api.audit.OperatorLog;
import io.yak.ops.api.audit.enums.AuditType;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.annotations.Operation;

class DataSyncControllerAuditTest {

    @Test
    void writeEndpointsUseExistingAuditAspect() {
        assertAudit("createTask", AuditType.DATA_SYNC_CREATE);
        assertAudit("updateTask", AuditType.DATA_SYNC_UPDATE);
        assertAudit("deleteTask", AuditType.DATA_SYNC_DELETE);
        assertAudit("publish", AuditType.DATA_SYNC_PUBLISH);
        assertAudit("unpublish", AuditType.DATA_SYNC_UNPUBLISH);
        assertAudit("start", AuditType.DATA_SYNC_START);
        assertAudit("stop", AuditType.DATA_SYNC_STOP);
    }

    private static void assertAudit(String methodName, AuditType expected) {
        for (Method method : DataSyncController.class.getDeclaredMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            OperatorLog annotation = method.getAnnotation(OperatorLog.class);
            assertNotNull(annotation);
            assertNotNull(method.getAnnotation(Operation.class));
            assertEquals(expected, annotation.auditType());
            return;
        }
        fail("No such controller method: " + methodName);
    }
}
