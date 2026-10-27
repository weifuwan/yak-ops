package io.yak.ops.connector.cdc.mysql.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlHybridEnumeratorStateSerializer;
import io.yak.ops.connector.cdc.mysql.source.enumerator.MySqlPendingSplitsStateSerializer;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlBinlogSplitSerializer;
import io.yak.ops.connector.cdc.mysql.source.split.MySqlHybridSplitSerializer;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies the unified entry keeps the existing mode-specific runtime and checkpoint contracts. */
class MySqlSourceEntryTest {

    @Test
    void initialUsesOriginalHybridStateAndSerializers() {
        var source = MySqlSource.create(builder(), StartupMode.INITIAL, 512, new TableMetrics());
        assertEquals(StartupMode.INITIAL, source.startupMode());
        assertEquals(512, source.snapshotChunkSize());
        assertInstanceOf(MySqlHybridCdcSource.class, source.delegate());
        assertInstanceOf(MySqlHybridSplitSerializer.class, source.getSplitSerializer());
        assertInstanceOf(MySqlHybridEnumeratorStateSerializer.class, source.getEnumeratorCheckpointSerializer());
        assertSame(source.delegate().getSplitSerializer(), source.getSplitSerializer());
        assertSame(source.delegate().getEnumeratorCheckpointSerializer(), source.getEnumeratorCheckpointSerializer());
    }

    @Test
    void latestUsesOriginalBinlogStateAndSerializers() {
        var source = MySqlSource.create(builder(), StartupMode.LATEST_OFFSET, 512, new TableMetrics());
        assertEquals(StartupMode.LATEST_OFFSET, source.startupMode());
        assertThrows(IllegalStateException.class, source::snapshotChunkSize);
        assertInstanceOf(MySqlCdcSource.class, source.delegate());
        assertInstanceOf(MySqlBinlogSplitSerializer.class, source.getSplitSerializer());
        assertInstanceOf(MySqlPendingSplitsStateSerializer.class, source.getEnumeratorCheckpointSerializer());
        assertSame(source.delegate().getSplitSerializer(), source.getSplitSerializer());
        assertSame(source.delegate().getEnumeratorCheckpointSerializer(), source.getEnumeratorCheckpointSerializer());
    }

    private static MySqlCdcSourceBuilder builder() {
        TableSchema schema = new TableSchema(
                List.of(new Column("id", LogicalTypes.BIGINT.copy(false))), List.of("id"));
        return MySqlSource.builder()
                .hostname("localhost")
                .username("test")
                .password("test")
                .serverId(8123)
                .topicPrefix("mysql-source-entry")
                .table(new TableId("shop", null, "orders"), schema);
    }
}
