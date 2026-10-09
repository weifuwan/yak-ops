package io.yak.ops.business.datasync.execution.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.common.bean.vo.datasync.DataSyncRetryPolicyVO;
import io.yak.ops.common.enums.datasync.DataSyncRetryPolicyMode;
import org.junit.jupiter.api.Test;

class ExecutorStructureContractTest {

    @Test
    void legacyAndFixedRetryDefaultsRemainStable() {
        ExecutionRetryPolicy legacy = ExecutionRetryPolicy.from(null);
        assertTrue(legacy.isFixed());
        assertEquals(1, legacy.maxAttempts());
        assertEquals(60, legacy.baseBackoffSeconds());
        assertEquals(60, legacy.delayForAttempt(3, legacy.baseBackoffSeconds()));

        DataSyncRetryPolicyVO configured = new DataSyncRetryPolicyVO();
        configured.setMode(DataSyncRetryPolicyMode.FIXED);
        configured.setMaxAttempts(0);
        configured.setBackoffSeconds(-2);
        ExecutionRetryPolicy policy = ExecutionRetryPolicy.from(configured);
        assertTrue(policy.isFixed());
        assertEquals(1, policy.maxAttempts());
        assertEquals(0, policy.baseBackoffSeconds());
        assertEquals(0, policy.delayForAttempt(5, -10));
    }

    @Test
    void smartBackoffPreservesMultiplierAndCap() {
        DataSyncRetryPolicyVO configured = new DataSyncRetryPolicyVO();
        configured.setMode(DataSyncRetryPolicyMode.SMART);
        configured.setMaxAttempts(7);
        configured.setBackoffSeconds(60);
        ExecutionRetryPolicy policy = ExecutionRetryPolicy.from(configured);
        assertFalse(policy.isFixed());
        assertEquals(7, policy.maxAttempts());
        assertEquals(60, policy.delayForAttempt(1, 60));
        assertEquals(120, policy.delayForAttempt(2, 60));
        assertEquals(240, policy.delayForAttempt(3, 60));
        assertEquals(300, policy.delayForAttempt(4, 60));
        assertEquals(300, policy.delayForAttempt(8, 60));
    }

    @Test
    void errorMessagesRemainMaskedBoundedAndKeepModeSpecificFallbacks() {
        assertEquals(
                DataSyncErrorCode.EXECUTION_FAILED.getMessage(), ExecutionErrorMessages.attemptFailure(null));
        assertEquals("IllegalArgumentException", ExecutionErrorMessages.attemptFailure(new IllegalArgumentException("")));
        assertEquals("表级执行失败", ExecutionErrorMessages.tableFailure((Throwable) null));
        assertEquals("表级执行失败", ExecutionErrorMessages.tableFailure(new RuntimeException(" ")));
        assertEquals("", ExecutionErrorMessages.tableFailure(""));
        assertEquals(1000, ExecutionErrorMessages.tableFailure("x".repeat(1200)).length());
        String masked = ExecutionErrorMessages.attemptFailure(new RuntimeException("password=secret token=abc"));
        assertFalse(masked.contains("secret"));
        assertFalse(masked.contains("abc"));
        assertTrue(masked.contains("******"));
    }
}
