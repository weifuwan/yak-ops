package io.yak.ops.connector.cdc.mysql.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.cdc.mysql.source.assigner.MySqlChunkSplitter;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies explicit keyless Source admission without relaxing legacy keyed admission. */
class MySqlKeylessSourceContractTest {
    private static final TableSchema KEYLESS = new TableSchema(
            List.of(
                    new Column("bucket", LogicalTypes.BIGINT.copy(false)),
                    new Column("amount", LogicalTypes.decimal(16, 2)),
                    new Column("name", LogicalTypes.varchar(60))),
            List.of());

    @Test
    void requiresExplicitNonNullNumericChunkColumnWithoutPhysicalPrimaryKey() {
        assertEquals("bucket", MySqlKeylessSourceContract.validate(KEYLESS, "bucket").name());
        assertThrows(IllegalArgumentException.class, () -> MySqlKeylessSourceContract.validate(KEYLESS, null));
        assertThrows(IllegalArgumentException.class, () -> MySqlKeylessSourceContract.validate(KEYLESS, "missing"));
        assertThrows(IllegalArgumentException.class, () -> MySqlKeylessSourceContract.validate(KEYLESS, "amount"));
        assertThrows(IllegalArgumentException.class, () -> MySqlKeylessSourceContract.validate(KEYLESS, "name"));
        TableSchema keyed = new TableSchema(KEYLESS.columns(), List.of("bucket"));
        assertThrows(IllegalArgumentException.class, () -> MySqlKeylessSourceContract.validate(keyed, "bucket"));
    }

    @Test
    void admitsOnlyExplicitKeylessMetadata() {
        Configuration settings = new Configuration()
                .set(MySqlCdcSourceOptions.KEYLESS_ENABLED, true)
                .set(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN, "bucket");
        new MySqlCdcSourceFactory().validateSchema(settings.toReadonlyConfig(), KEYLESS);
        assertThrows(IllegalArgumentException.class,
                () -> new MySqlCdcSourceFactory().validateSchema(
                        new Configuration().set(MySqlCdcSourceOptions.KEYLESS_ENABLED, true).toReadonlyConfig(),
                        KEYLESS));
    }

    @Test
    void keylessHybridCanBeBuiltWhileKeyedLegacyValidationRemainsStrict() {
        var table = new io.yak.ops.core.data.TableId("app", null, "events");
        var source = MySqlCdcSource.builder()
                .hostname("localhost")
                .username("replication")
                .password("test")
                .serverId(7731)
                .topicPrefix("keyless-constructor")
                .table(table, KEYLESS, "bucket")
                .buildHybrid(2);
        assertEquals(io.yak.ops.core.api.connector.source.Boundedness.CONTINUOUS_UNBOUNDED,
                source.getBoundedness());
        assertThrows(IllegalArgumentException.class, () -> MySqlCdcSource.builder()
                .hostname("localhost")
                .username("replication")
                .password("test")
                .serverId(7732)
                .topicPrefix("keyless-no-selection")
                .table(table, KEYLESS)
                .buildHybrid(2));
        var keyed = new TableSchema(KEYLESS.columns(), List.of("bucket"));
        assertThrows(IllegalArgumentException.class, () -> MySqlCdcSource.builder()
                .hostname("localhost")
                .username("replication")
                .password("test")
                .serverId(7733)
                .topicPrefix("keyed-other-chunk")
                .table(table, keyed, "amount")
                .buildHybrid(2));
    }

    @Test
    void keepsExistingBigintAdmissionUnchangedWhenKeylessIsNotRequested() {
        var factory = new MySqlCdcSourceFactory();
        var keyed = new TableSchema(KEYLESS.columns(), List.of("bucket"));
        factory.validateSchema(new Configuration().toReadonlyConfig(), keyed);
        assertEquals("bucket", MySqlChunkSplitter.splitKey(keyed));
        assertThrows(IllegalArgumentException.class,
                () -> factory.validateSchema(new Configuration().toReadonlyConfig(), KEYLESS));
    }

    @Test
    void rejectsUnresolvedAndNullableChunkFields() {
        var unknown = new TableSchema(
                List.of(new Column("bucket", LogicalTypes.decimal(null, null))), List.of());
        assertThrows(IllegalArgumentException.class, () -> MySqlKeylessSourceContract.validate(unknown, "bucket"));
        var nullable = new TableSchema(
                List.of(new Column("bucket", LogicalTypes.BIGINT)), List.of());
        assertThrows(IllegalArgumentException.class, () -> MySqlKeylessSourceContract.validate(nullable, "bucket"));
    }
}
