package io.yak.ops.boot.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.common.bean.dto.task.PublishDefinitionDTO;
import io.yak.ops.common.bean.dto.task.UpdateDefinitionDTO;
import io.yak.ops.common.bean.vo.task.DefinitionVO;
import io.yak.ops.common.bean.vo.task.DefinitionVersionSummaryVO;
import io.yak.ops.common.bean.vo.task.DefinitionVersionVO;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

/** HTTP JSON and required preconditions for editing, publication and lazy version detail. */
class TaskDefinitionContractTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void publishingRequiresVersionRevisionAndStableRequestKey() throws Exception {
        PublishDefinitionDTO request = json.readValue(
                "{\"version\":3,\"expectedRevision\":7,\"requestId\":\"publish-123\"}", PublishDefinitionDTO.class);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(request).isEmpty());
            request.setExpectedRevision(null);
            assertFalse(validator.validate(request).isEmpty());
            request.setExpectedRevision(7L);
            request.setVersion(null);
            assertFalse(validator.validate(request).isEmpty());
            request.setVersion(3);
            request.setRequestId(null);
            assertFalse(validator.validate(request).isEmpty());
            request.setRequestId("same request with whitespace");
            assertFalse(validator.validate(request).isEmpty());
        }
    }

    @Test
    void updateRequiresRevisionAndRetainsStructuredParameterContract() throws Exception {
        UpdateDefinitionDTO update = json.readValue(
                "{\"name\":\"orders\",\"expectedRevision\":4,\"parameters\":{\"source\":{}}}",
                UpdateDefinitionDTO.class);
        assertTrue(json.readTree(update.getParameters()).isObject());
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(update).isEmpty());
            update.setExpectedRevision(null);
            assertFalse(factory.getValidator().validate(update).isEmpty());
        }
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                () -> json.readValue("{\"name\":\"orders\",\"expectedRevision\":4,\"parameters\":\"{}\"}",
                        UpdateDefinitionDTO.class));
    }

    @Test
    void summaryOmitsPayloadWhileDetailAndPublicationStateUseExpectedJsonFields() throws Exception {
        DefinitionVersionSummaryVO summary = new DefinitionVersionSummaryVO();
        summary.setVersion(3);
        summary.setCreateBy("editor");
        assertFalse(json.readTree(json.writeValueAsString(summary)).has("parameters"));
        DefinitionVersionVO detail = new DefinitionVersionVO();
        detail.setVersion(3);
        detail.setParameters("{\"parallelism\":2}");
        JsonNode snapshot = json.readTree(json.writeValueAsString(detail));
        assertTrue(snapshot.path("parameters").isObject());
        assertEquals(2, snapshot.path("parameters").path("parallelism").asInt());

        DefinitionVO definition = new DefinitionVO();
        definition.setDefinitionVersion(4);
        definition.setPublishedVersion(3);
        definition.setPublishedDebug(false);
        definition.setRevision(9);
        definition.setHasUnpublishedChanges(true);
        JsonNode state = json.readTree(json.writeValueAsString(definition));
        assertEquals(4, state.path("definitionVersion").asInt());
        assertEquals(3, state.path("publishedVersion").asInt());
        assertEquals(9, state.path("revision").asLong());
        assertTrue(state.path("hasUnpublishedChanges").asBoolean());
        assertFalse(state.path("publishedDebug").asBoolean());
    }
}
