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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Defines a bounded JDBC source for one or more tables using a shared split enumerator.
 *
 * <p>Source definitions are reusable and never retain a JDBC connection. A coordinator
 * discovers tables and numeric-key splits; independently owned fetchers perform blocking
 * JDBC reads, while mailbox-side readers advance checkpoint positions after emission.
 *
 * <p>A supported numeric primary key permits per-split cursor recovery. Other tables replay
 * an entire split, and concurrent source-table changes are not a globally consistent snapshot.
 */
public final class JdbcSource implements Source<TableRecord, JdbcSourceSplit, JdbcEnumeratorState> {

    private final JdbcConnectionProvider connectionProvider;
    private final String jdbcUrl;
    private final List<TableId> tables;
    private final Map<TableId, List<String>> projections;
    private final Configuration configuration;
    private final JdbcDialect dialect;
    private final String fingerprint;
    private final JdbcSourceSplitSerializer splitSerializer = new JdbcSourceSplitSerializer();
    private final JdbcEnumeratorStateSerializer stateSerializer = new JdbcEnumeratorStateSerializer();

    public JdbcSource(JdbcConnectionOptions connection, List<TableId> tables, Configuration configuration) {
        this(connection, tables, configuration, Map.of());
    }

    public JdbcSource(
            JdbcConnectionOptions connection,
            List<TableId> tables,
            Configuration configuration,
            Map<TableId, List<String>> projections) {
        this(new DriverManagerJdbcConnectionProvider(connection), connection.url(), tables, configuration, projections);
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
        this(connectionProvider, jdbcUrl, tables, configuration, Map.of());
    }

    /**
     * Creates a source with a caller-provided connection strategy and per-table projections.
     *
     * <p>Table IDs must be unique; projection columns must be nonempty and unique per table.
     * Configuration is copied, and the source definition is fingerprinted for checkpoint
     * compatibility without opening a connection.
     *
     * @param connectionProvider source of independent enumerator and fetcher connections
     * @param jdbcUrl vendor URL used to resolve a JDBC dialect
     * @param tables nonempty, ordered source-table IDs
     * @param configuration effective source planning and reader options
     * @param projections optional ordered columns per source table
     */
    public JdbcSource(
            JdbcConnectionProvider connectionProvider,
            String jdbcUrl,
            List<TableId> tables,
            Configuration configuration,
            Map<TableId, List<String>> projections) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        this.tables = List.copyOf(Objects.requireNonNull(tables, "tables"));
        this.configuration = new Configuration(Objects.requireNonNull(configuration, "configuration"));
        if (this.tables.isEmpty() || new HashSet<>(this.tables).size() != this.tables.size()) {
            throw new IllegalArgumentException("JDBC source tables must be nonempty and unique");
        }
        Map<TableId, List<String>> selected = new LinkedHashMap<>();
        for (Map.Entry<TableId, List<String>> entry :
                Objects.requireNonNull(projections, "projections").entrySet()) {
            List<String> columns = List.copyOf(entry.getValue());
            if (!this.tables.contains(entry.getKey())
                    || columns.isEmpty()
                    || columns.stream().anyMatch(column -> column.isBlank())
                    || new HashSet<>(columns).size() != columns.size()) {
                throw new IllegalArgumentException("Invalid JDBC source table projection");
            }
            selected.put(entry.getKey(), columns);
        }
        this.projections = Map.copyOf(selected);
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

    /**
     * Restores split planning only when the saved source definition fingerprint still matches.
     *
     * <p>Incompatible tables, projections, or split policy are rejected before a reader starts;
     * the resumed coordinator restores only unassigned work from its own snapshot.
     */
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
                context,
                new JdbcSplitPlanner(connectionProvider, dialect, configuration, projections),
                tables,
                fingerprint,
                restored);
    }

    private void validateOptions() {
        if (configuration.get(JdbcSourceOptions.TARGET_ROWS_PER_SPLIT) <= 0
                || configuration.get(JdbcSourceOptions.MAX_SPLITS_PER_TABLE) <= 0
                || configuration.get(JdbcSourceOptions.READER_FETCH_BATCH_SIZE) <= 0
                || configuration.get(JdbcSourceOptions.RESULT_SET_FETCH_SIZE) <= 0
                || configuration.get(JdbcSourceOptions.QUERY_TIMEOUT_SECONDS) <= 0
                || configuration.get(JdbcSourceOptions.CONNECTION_ATTEMPTS) <= 0) {
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
            List<String> projected = projections.get(table);
            if (projected != null) {
                definition.append("|projection:").append(projected.size());
                for (String column : projected) {
                    definition.append('|').append(identifierPart(column));
                }
            }
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
