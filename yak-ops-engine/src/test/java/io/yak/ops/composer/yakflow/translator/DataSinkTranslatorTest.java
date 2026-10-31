package io.yak.ops.composer.yakflow.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.yak.ops.core.api.connector.sink.Sink;
import io.yak.ops.core.api.dag.Transformation;
import io.yak.ops.core.data.TableRecord;
import io.yak.ops.flow.runtime.transformations.SinkTransformation;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataSinkTranslatorTest {

    @Test
    void translatesOtherRecordTypesWithoutDependingOnTableRecord() {
        Transformation<String> transformed =
                new Transformation<String>("string-transform", String.class) {
                    @Override
                    public List<Transformation<?>> getInputs() {
                        return List.of();
                    }
                };
        Sink<String> sink = context -> {
            throw new UnsupportedOperationException("Not executed during translation");
        };

        SinkTransformation<String> output =
                new DataSinkTranslator().translate(transformed, sink, 1, "string-definition");

        assertSame(transformed, output.getInput());
        assertSame(sink, output.getSink());
        assertEquals(String.class, output.getInputType());
        assertEquals("data-sync-sink-string-definition", output.getUid());
    }

    @Test
    void acceptsTransformedInputInsteadOfOnlySourceTransformations() {
        Transformation<TableRecord> transformed =
                new Transformation<TableRecord>("post-transform", TableRecord.class) {
                    @Override
                    public List<Transformation<?>> getInputs() {
                        return List.of();
                    }
                };
        Sink<TableRecord> sink = context -> {
            throw new UnsupportedOperationException("Not executed during translation");
        };

        SinkTransformation<TableRecord> output =
                new DataSinkTranslator().translate(transformed, sink, 2, "definition-1");

        assertSame(transformed, output.getInput());
        assertSame(sink, output.getSink());
        assertEquals(2, output.getParallelism());
        assertEquals("data-sync-sink-definition-1", output.getUid());
    }
}
