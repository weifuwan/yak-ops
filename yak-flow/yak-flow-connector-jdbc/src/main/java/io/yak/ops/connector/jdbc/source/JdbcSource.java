package io.yak.ops.connector.jdbc.source;

import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.JdbcSourceOptions;
import io.yak.ops.connector.jdbc.database.connection.DriverManagerJdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.connection.JdbcConnectionProvider;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialect;
import io.yak.ops.connector.jdbc.database.dialect.JdbcDialects;
import io.yak.ops.connector.jdbc.source.enumerator.JdbcEnumeratorState;
import io.yak.ops.connector.jdbc.source.enumerator.JdbcEnumeratorStateSerializer;
import io.yak.ops.connector.jdbc.source.enumerator.JdbcSourceEnumerator;
import io.yak.ops.connector.jdbc.source.enumerator.JdbcSplitPlanner;
import io.yak.ops.connector.jdbc.source.reader.JdbcSourceReader;
import io.yak.ops.connector.jdbc.source.split.JdbcSourceSplit;
import io.yak.ops.connector.jdbc.source.split.JdbcSourceSplitSerializer;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.core.api.connector.source.Source;
import io.yak.ops.core.api.connector.source.SourceReader;
import io.yak.ops.core.api.connector.source.SourceReaderContext;
import io.yak.ops.core.api.connector.source.SplitEnumerator;
import io.yak.ops.core.api.connector.source.SplitEnumeratorContext;
import io.yak.ops.core.api.io.SimpleVersionedSerializer;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.data.TableRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Bounded JDBC source for one or many database tables, using one shared split enumerator.
 *
 * <p>The source definition never opens or retains an active JDBC connection. A table may yield
 * multiple disjoint numeric-primary-key splits; a single table follows the same path as many.
 */
public final class JdbcSource implements Source<TableRecord, JdbcSourceSplit, JdbcEnumeratorState> {

    private final JdbcConnectionProvider connectionProvider;
    private final String jdbcUrl;
    private final List<TableId> tables;
    private final Configuration configuration;
    private final JdbcDialect dialect;
    private final String fingerprint;
    private final JdbcSourceSplitSerializer splitSerializer = new JdbcSourceSplitSerializer();
    private final JdbcEnumeratorStateSerializer stateSerializer = new JdbcEnumeratorStateSerializer();

    public JdbcSource(JdbcConnectionOptions connection, List<TableId> tables, Configuration configuration) {
        this(new DriverManagerJdbcConnectionProvider(connection), connection.url(), tables, configuration);
    }

    public JdbcSource(JdbcConnectionOptions connection, List<TableId> tables) {
        this(connection, tables, new Configuration());
    }

    /**
     * Injects a driver-isolated or tunneled connection provider without depending on
     * Datasource Plugin or a product task model.
     */
    public JdbcSource(
            JdbcConnectionProvider connectionProvider,
            String jdbcUrl,
            List<TableId> tables,
            Configuration configuration) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        this.tables = List.copyOf(Objects.requireNonNull(tables, "tables"));
        this.configuration = new Configuration(Objects.requireNonNull(configuration, "configuration"));
        if (this.tables.isEmpty() || new HashSet<>(this.tables).size() != this.tables.size()) {
            throw new IllegalArgumentException("JDBC source tables must be nonempty and unique");
        }
        dialect = JdbcDialects.forUrl(jdbcUrl);
        validateOptions();
        fingerprint = definitionFingerprint();
    }

    @Override
    public Boundedness getBoundedness() {
        return Boundedness.BOUNDED;
    }

    @Override
    public SplitEnumerator<JdbcSourceSplit, JdbcEnumeratorState> createEnumerator(
            SplitEnumeratorContext<JdbcSourceSplit> context) {
        return newEnumerator(context, null);
    }

    @Override
    public SplitEnumerator<JdbcSourceSplit, JdbcEnumeratorState> restoreEnumerator(
            SplitEnumeratorContext<JdbcSourceSplit> context, JdbcEnumeratorState state) {
        JdbcEnumeratorState checkpoint = Objects.requireNonNull(state, "state");
        if (!fingerprint.equals(checkpoint.sourceFingerprint())) {
            throw new IllegalArgumentException("The JDBC source definition changed since its checkpoint");
        }
        return newEnumerator(context, checkpoint);
    }

    @Override
    public SourceReader<TableRecord, JdbcSourceSplit> createReader(SourceReaderContext context) {
        return new JdbcSourceReader(connectionProvider, dialect, configuration, context);
    }

    @Override
    public SimpleVersionedSerializer<JdbcSourceSplit> getSplitSerializer() {
        return splitSerializer;
    }

    @Override
    public SimpleVersionedSerializer<JdbcEnumeratorState> getEnumeratorCheckpointSerializer() {
        return stateSerializer;
    }

    private JdbcSourceEnumerator newEnumerator(
            SplitEnumeratorContext<JdbcSourceSplit> context, JdbcEnumeratorState restored) {
        return new JdbcSourceEnumerator(
                context, new JdbcSplitPlanner(connectionProvider, dialect, configuration), tables, fingerprint, restored);
    }

    private void validateOptions() {
        if (configuration.get(JdbcSourceOptions.TARGET_ROWS_PER_SPLIT) <= 0
                || configuration.get(JdbcSourceOptions.MAX_SPLITS_PER_TABLE) <= 0
                || configuration.get(JdbcSourceOptions.READER_FETCH_BATCH_SIZE) <= 0
                || configuration.get(JdbcSourceOptions.RESULT_SET_FETCH_SIZE) <= 0
                || configuration.get(JdbcSourceOptions.QUERY_TIMEOUT_SECONDS) <= 0) {
            throw new IllegalArgumentException("JDBC source options must be positive");
        }
    }

    private String identifierPart(String value) {
        return value == null ? "-" : value.length() + ":" + value;
    }

    private String definitionFingerprint() {
        StringBuilder definition = new StringBuilder(jdbcUrl)
                .append('|')
                .append(configuration.get(JdbcSourceOptions.TARGET_ROWS_PER_SPLIT))
                .append('|')
                .append(configuration.get(JdbcSourceOptions.MAX_SPLITS_PER_TABLE));
        for (TableId table : tables) {
            definition
                    .append('|')
                    .append(identifierPart(table.catalog()))
                    .append('|')
                    .append(identifierPart(table.schema()))
                    .append('|')
                    .append(identifierPart(table.table()));
        }
        try {
            byte[] bytes = definition.toString().getBytes(StandardCharsets.UTF_8);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required", impossible);
        }
    }
}
