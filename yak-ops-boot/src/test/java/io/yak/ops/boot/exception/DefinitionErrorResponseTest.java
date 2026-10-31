package io.yak.ops.boot.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.yak.ops.common.enums.task.DefinitionErrorCode;
import io.yak.ops.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Editing conflicts and publication failures expose localized errors without raw plugin diagnostics. */
class DefinitionErrorResponseTest {
    @Test
    void staleWritesAndConflictingPublicationRequestsAreConflicts() {
        for (DefinitionErrorCode code : new DefinitionErrorCode[] {
                DefinitionErrorCode.REVISION_CONFLICT,
                DefinitionErrorCode.PUBLICATION_REQUEST_CONFLICT,
                DefinitionErrorCode.SCHEDULE_INCOMPATIBLE}) {
            var response = new GlobalExceptionHandler().handleBusinessException(new BusinessException(code));
            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            assertEquals(code.getCode(), response.getBody().getCode());
            assertEquals(code.getMessage(), response.getBody().getMessage());
        }
    }

    @Test
    void publicationValidationDoesNotExposeCauseDetails() {
        BusinessException failure = new BusinessException(
                DefinitionErrorCode.PUBLICATION_INVALID, new IllegalArgumentException("private-connection-value"));
        var response = new GlobalExceptionHandler().handleBusinessException(failure);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(DefinitionErrorCode.PUBLICATION_INVALID.getCode(), response.getBody().getCode());
        assertFalse(response.getBody().getMessage().contains("private-connection-value"));
    }
}
