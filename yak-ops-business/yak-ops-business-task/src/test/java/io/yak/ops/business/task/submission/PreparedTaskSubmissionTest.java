package io.yak.ops.business.task.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.yak.ops.common.enums.task.TaskTriggerType;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import io.yak.ops.plugin.task.api.execution.TaskExecutionResources;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreparedTaskSubmissionTest {

    @Test
    void preservesAdmittedInputsAndExecutablePluginIdentity() {
        TaskExecutionIdentity identity = new TaskExecutionIdentity("workspace", "definition", 2, "instance");
        ReadonlyConfig parameters = ReadonlyConfig.fromMap(java.util.Map.of());
        TaskChannel plugin = mock(TaskChannel.class);
        TaskExecutionResources plan = new TaskExecutionResources(List.of(), "resource-revision");

        PreparedTaskSubmission request = new PreparedTaskSubmission(
                identity,
                true,
                TaskTriggerType.MANUAL,
                "private-request-key",
                "intent",
                "actor",
                parameters,
                plan,
                plugin,
                null,
                null,
                null);
        PreparedTaskSubmission another = new PreparedTaskSubmission(
                identity,
                true,
                TaskTriggerType.MANUAL,
                "private-request-key",
                "intent",
                "actor",
                parameters,
                plan,
                plugin,
                null,
                null,
                null);

        assertSame(identity, request.identity());
        assertTrue(request.debug());
        assertEquals(TaskTriggerType.MANUAL, request.triggerType());
        assertEquals("private-request-key", request.requestId());
        assertEquals("intent", request.requestFingerprint());
        assertEquals("actor", request.operatorId());
        assertSame(parameters, request.parameters());
        assertSame(plan, request.plan());
        assertSame(plugin, request.plugin());
        assertNotEquals(request, another);
        assertFalse(request.getClass().isRecord());
        verifyNoInteractions(plugin);
    }

    @Test
    void diagnosticsNeverInvokePluginOrRenderSubmissionInputs() {
        TaskChannel plugin = mock(TaskChannel.class);
        ReadonlyConfig parameters = mock(ReadonlyConfig.class);
        PreparedTaskSubmission request = new PreparedTaskSubmission(
                new TaskExecutionIdentity("workspace", "definition", 2, "instance"),
                false,
                TaskTriggerType.WORKFLOW,
                "private-request-key",
                "private-fingerprint",
                "sensitive-actor",
                parameters,
                new TaskExecutionResources(List.of(), "private-resource-revision"),
                plugin,
                null,
                null,
                null);

        String diagnostic = request.toString();
        assertEquals(TaskTriggerType.WORKFLOW, request.triggerType());
        assertTrue(diagnostic.contains("redacted"));
        assertFalse(diagnostic.contains("private-request-key"));
        assertFalse(diagnostic.contains("private-fingerprint"));
        assertFalse(diagnostic.contains("private-resource-revision"));
        assertFalse(diagnostic.contains("sensitive-actor"));
        verifyNoInteractions(plugin);
        verifyNoInteractions(parameters);
    }
}
