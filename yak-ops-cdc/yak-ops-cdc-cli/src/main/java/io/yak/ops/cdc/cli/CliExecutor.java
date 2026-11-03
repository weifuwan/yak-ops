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

import io.yak.ops.cdc.cli.parser.PipelineDefinitionParser;
import io.yak.ops.cdc.cli.parser.YamlPipelineDefinitionParser;
import io.yak.ops.cdc.common.configuration.Configuration;
import io.yak.ops.cdc.composer.PipelineComposer;
import io.yak.ops.cdc.composer.PipelineExecution;
import io.yak.ops.cdc.composer.definition.PipelineDef;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Executor for parsing a pipeline definition, composing it, and executing it through a supplied
 * {@link PipelineComposer}.
 *
 * <p>The executor deliberately does not select or configure a deployment target. The runtime
 * implementation is supplied through {@link PipelineComposer}.
 */
public class CliExecutor {

    private final Path pipelineDefPath;
    private final Configuration globalPipelineConfig;
    private final PipelineComposer composer;

    public CliExecutor(
            Path pipelineDefPath,
            Configuration globalPipelineConfig,
            PipelineComposer composer) {
        this.pipelineDefPath =
                Objects.requireNonNull(pipelineDefPath, "pipelineDefPath must not be null");
        this.globalPipelineConfig =
                Objects.requireNonNull(
                        globalPipelineConfig, "globalPipelineConfig must not be null");
        this.composer = Objects.requireNonNull(composer, "composer must not be null");
    }

    /** Parse, compose, and execute the pipeline definition. */
    public PipelineExecution.ExecutionInfo run() throws Exception {
        return deployWithComposer(composer);
    }

    private PipelineExecution.ExecutionInfo deployWithComposer(PipelineComposer composer)
            throws Exception {
        PipelineDefinitionParser pipelineDefinitionParser = new YamlPipelineDefinitionParser();
        PipelineDef pipelineDef =
                pipelineDefinitionParser.parse(pipelineDefPath, globalPipelineConfig);
        PipelineExecution execution = composer.compose(pipelineDef);
        return execution.execute();
    }
}
