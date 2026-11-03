/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yak.ops.cdc.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.cdc.common.configuration.Configuration;
import io.yak.ops.cdc.common.pipeline.PipelineOptions;
import io.yak.ops.cdc.composer.PipelineComposer;
import io.yak.ops.cdc.composer.PipelineExecution;
import io.yak.ops.cdc.composer.definition.PipelineDef;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliExecutorTest {

    @TempDir private Path tempDirectory;

    @Test
    void runParsesDefinitionComposesAndExecutesPipeline() throws Exception {
        Path pipelineDefinition = tempDirectory.resolve("pipeline.yaml");
        Files.writeString(
                pipelineDefinition,
                "source:\n  type: mysql\nsink:\n  type: values\n");

        AtomicReference<PipelineDef> composedDefinition = new AtomicReference<>();
        AtomicBoolean executionInvoked = new AtomicBoolean();
        PipelineExecution.ExecutionInfo expectedInfo =
                new PipelineExecution.ExecutionInfo("execution-1", "test pipeline");

        PipelineComposer composer =
                definition -> {
                    composedDefinition.set(definition);
                    return () -> {
                        executionInvoked.set(true);
                        return expectedInfo;
                    };
                };

        CliExecutor executor =
                new CliExecutor(
                        pipelineDefinition,
                        Configuration.fromMap(Map.of("parallelism", "7")),
                        composer);

        PipelineExecution.ExecutionInfo actualInfo = executor.run();

        assertSame(expectedInfo, actualInfo);
        assertEquals("mysql", composedDefinition.get().getSource().getType());
        assertEquals("values", composedDefinition.get().getSink().getType());
        assertEquals(
                7,
                composedDefinition
                        .get()
                        .getConfig()
                        .get(PipelineOptions.PIPELINE_PARALLELISM));
        assertTrue(executionInvoked.get());
    }

    @Test
    void runDoesNotComposeWhenPipelineDefinitionIsInvalid() throws Exception {
        Path pipelineDefinition = tempDirectory.resolve("invalid-pipeline.yaml");
        Files.writeString(pipelineDefinition, "source:\n  type: mysql\n");

        AtomicBoolean composerInvoked = new AtomicBoolean();
        PipelineComposer composer =
                definition -> {
                    composerInvoked.set(true);
                    return () -> new PipelineExecution.ExecutionInfo("unexpected", "unexpected");
                };

        CliExecutor executor =
                new CliExecutor(pipelineDefinition, new Configuration(), composer);

        assertThrows(Exception.class, executor::run);
        assertFalse(composerInvoked.get());
    }

    @Test
    void runPropagatesPipelineExecutionFailure() throws Exception {
        Path pipelineDefinition = tempDirectory.resolve("pipeline.yaml");
        Files.writeString(
                pipelineDefinition,
                "source:\n  type: mysql\nsink:\n  type: values\n");

        IllegalStateException expectedFailure =
                new IllegalStateException("runtime execution failed");
        PipelineComposer composer =
                definition ->
                        () -> {
                            throw expectedFailure;
                        };

        CliExecutor executor =
                new CliExecutor(pipelineDefinition, new Configuration(), composer);

        assertSame(expectedFailure, assertThrows(IllegalStateException.class, executor::run));
    }
}
