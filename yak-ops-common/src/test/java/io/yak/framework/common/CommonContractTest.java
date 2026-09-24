package io.yak.framework.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommonContractTest {

    private static final ErrorCode TEST_ERROR = new ErrorCode() {
        public Integer getCode() {
            return 40001;
        }

        public String getMessage() {
            return "作业不存在";
        }
    };

    @Test
    void shouldBuildResultFromModuleErrorCode() {
        Result<Void> result = Result.fail(TEST_ERROR);

        assertEquals(40001, result.getCode());
        assertEquals("作业不存在", result.getMessage());
        assertTrue(result.failed());
    }

    @Test
    void shouldPreserveStructuredErrorInBusinessException() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> Assert.notNull(null, TEST_ERROR));

        assertEquals(TEST_ERROR, exception.getErrorCode());
        assertEquals(40001, Result.fail(exception).getCode());
    }
}
