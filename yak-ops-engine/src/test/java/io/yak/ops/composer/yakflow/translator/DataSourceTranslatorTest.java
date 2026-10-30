package io.yak.ops.composer.yakflow.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SourceSplit;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.flow.runtime.transformations.SourceTransformation;
import org.junit.jupiter.api.Test;

class DataSourceTranslatorTest {

    @Test
    void usesTheSourceContractAndWorksWithOtherRecordTypes() {
        Source<String, SourceSplit, Object> source = new Source<String, SourceSplit, Object>() {
            @Override
            public Boundedness getBoundedness() {
                return Boundedness.BOUNDED;
            }

            @Override
            public SplitEnumerator<SourceSplit, Object> createEnumerator(
                    SplitEnumeratorContext<SourceSplit> context) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SplitEnumerator<SourceSplit, Object> restoreEnumerator(
                    SplitEnumeratorContext<SourceSplit> context, Object checkpointState) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SourceReader<String, SourceSplit> createReader(SourceReaderContext context) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SimpleVersionedSerializer<SourceSplit> getSplitSerializer() {
                throw new UnsupportedOperationException();
            }

            @Override
            public SimpleVersionedSerializer<Object> getEnumeratorCheckpointSerializer() {
                throw new UnsupportedOperationException();
            }
        };

        DataSourceTranslator translator = new DataSourceTranslator();
        assertThrows(
                IllegalArgumentException.class,
                () -> translator.translate(source, String.class, 0, "definition-1"));

        SourceTransformation<String> result =
                translator.translate(source, String.class, 2, "definition-1");
        assertSame(source, result.getSource());
        assertEquals(String.class, result.getOutputType());
        assertEquals(2, result.getParallelism());
        assertEquals("data-sync-source-definition-1", result.getUid());
    }
}
