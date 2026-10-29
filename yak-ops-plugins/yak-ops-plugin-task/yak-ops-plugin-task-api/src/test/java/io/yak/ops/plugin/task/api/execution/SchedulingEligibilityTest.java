package io.yak.ops.plugin.task.api.execution;

import static org.junit.jupiter.api.Assertions.assertFalse;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import java.util.concurrent.CompletionStage;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Manual execution plugins do not acquire scheduling capability implicitly. */
class SchedulingEligibilityTest {
    @Test
    void existingPluginDefaultsToManualOnly() {
        ExecutableTaskPlugin plugin = new ExecutableTaskPlugin() {
            @Override
            public String type() {
                return "MANUAL_TEST";
            }

            @Override
            public int parameterSchemaVersion() {
                return 1;
            }

            @Override
            public OptionRule optionRule() {
                return OptionRule.builder().build();
            }

            @Override
            public ReadonlyConfig parse(String content) {
                throw new AssertionError();
            }

            @Override
            public void validateReferences(ReadonlyConfig options, TaskValidationContext context) {
                throw new AssertionError();
            }

            @Override
            public void validate(ReadonlyConfig options, TaskValidationContext context) {
                throw new AssertionError();
            }

            @Override
            public TaskExecutionResources plan(ReadonlyConfig options, TaskExecutionIdentity context) {
                throw new AssertionError();
            }

            @Override
            public CompletionStage<TaskHandle> start(
                    ReadonlyConfig options, TaskExecutionResources plan, TaskExecutionIdentity context, boolean debug) {
                throw new AssertionError();
            }
        };
        assertFalse(plugin.supportsScheduling(ReadonlyConfig.fromMap(Map.of())));
    }
}
