package io.yak.ops.cli.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.composer.definition.PipelineDef;
import org.junit.jupiter.api.Test;

/** Locks the nested pipeline configuration behavior of upstream Flink CDC 3.7. */
class YamlPipelineDefinitionParserNestedConfigTest {

    @Test
    void flattensNestedPipelineConfiguration() {
        PipelineDef definition = new YamlPipelineDefinitionParser().parse("""
                source:
                  type: mysql
                sink:
                  type: jdbc
                pipeline:
                  execution:
                    runtime-mode: BATCH
                  parallelism: 2
                """);

        assertEquals("BATCH", definition.getConfig().toMap().get("execution.runtime-mode"));
        assertEquals("2", definition.getConfig().toMap().get("parallelism"));
    }
}
