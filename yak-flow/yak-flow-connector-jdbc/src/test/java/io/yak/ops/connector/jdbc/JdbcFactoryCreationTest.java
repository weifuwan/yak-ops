package io.yak.ops.connector.jdbc;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.base.factory.TableSourceFactoryContext;
import io.yak.ops.connector.base.factory.TableSinkFactoryContext;
import io.yak.ops.connector.jdbc.source.JdbcSource;
import io.yak.ops.connector.jdbc.source.JdbcSourceFactory;
import io.yak.ops.connector.jdbc.sink.JdbcSink;
import io.yak.ops.connector.jdbc.sink.JdbcSinkFactory;
import io.yak.ops.connector.jdbc.sink.JdbcSinkOptions;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Checks Connector creation with configuration and metadata only. */
class JdbcFactoryCreationTest {
    private static final TableId TABLE = new TableId(null, "PUBLIC", "ITEMS");
    private static final TableSchema SCHEMA = new TableSchema(List.of(new Column("ID", LogicalTypes.BIGINT.copy(false))), List.of("ID"));

    @Test
    void buildsSourceAndSinkWithoutPlatformIdentityOrOpeningConnections() {
        Configuration configuration = new Configuration().set(JdbcConnectionOptions.URL, "jdbc:h2:mem:factory_inputs")
                .set(JdbcConnectionOptions.USERNAME, "sa").set(JdbcConnectionOptions.PASSWORD, "private-password")
                .set(JdbcConnectionOptions.DRIVER_CLASS, "driver.must.not.be.loaded.WhileCreating");
        var source = new TableSourceFactoryContext(configuration.toReadonlyConfig(), TABLE, SCHEMA);
        assertInstanceOf(JdbcSource.class, new JdbcSourceFactory().createSource(source));
        configuration.set(JdbcSinkOptions.WRITE_MODE, JdbcWriteMode.APPEND);
        var sink = new TableSinkFactoryContext(configuration.toReadonlyConfig(), TABLE, SCHEMA, TABLE, SCHEMA);
        assertInstanceOf(JdbcSink.class, new JdbcSinkFactory().createSink(sink));
        assertFalse(source.toString().contains("private-password"));
        assertFalse(sink.toString().contains("private-password"));
    }

    @Test
    void requiresResolvedConnectionInputsAndRejectsUnknownFields() {
        var missing = new TableSourceFactoryContext(io.yak.ops.configuration.ReadonlyConfig.fromMap(Map.of()), TABLE, SCHEMA);
        assertThrows(IllegalArgumentException.class, () -> new JdbcSourceFactory().createSource(missing));
        var unknown = new TableSourceFactoryContext(io.yak.ops.configuration.ReadonlyConfig.fromMap(
                Map.of(JdbcConnectionOptions.URL.key(), "jdbc:h2:mem:factory_inputs", "unregistered", true)), TABLE, SCHEMA);
        assertThrows(IllegalArgumentException.class, () -> new JdbcSourceFactory().createSource(unknown));
    }
}
