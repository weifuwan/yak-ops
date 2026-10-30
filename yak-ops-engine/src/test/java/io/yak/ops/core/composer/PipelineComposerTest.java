package io.yak.ops.core.composer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.definition.PipelineDef;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Composer interface remains aligned with Flink CDC's definition-to-execution boundary. */
class PipelineComposerTest {
    @Test
    void composerOnlyPlansAndComposesPipelines() {
        assertTrue(PipelineComposer.class.isInterface());
        List<String> methods = Arrays.stream(PipelineComposer.class.getDeclaredMethods())
                .map(Method::getName)
                .sorted()
                .toList();
        assertEquals(List.of("compose", "plan"), methods);
        assertFalse(methods.contains("submit"));
        assertFalse(methods.contains("cancel"));
        assertFalse(methods.contains("getJob"));
        assertEquals(PipelineExecution.class, Arrays.stream(PipelineComposer.class.getDeclaredMethods())
                .filter(method -> "compose".equals(method.getName()))
                .findFirst()
                .orElseThrow()
                .getReturnType());
        assertEquals(PipelineDef.class, Arrays.stream(PipelineComposer.class.getDeclaredMethods())
                .filter(method -> "compose".equals(method.getName()))
                .findFirst()
                .orElseThrow()
                .getParameterTypes()[0]);
    }
}
