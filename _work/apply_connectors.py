from apply_model import ROOT, P, C, J, edit, write

write(C+'metrics/TableMetrics.java', '''
package io.yak.ops.connector.base.metrics;

import io.yak.ops.core.data.TableId;
import java.util.LinkedHashMap;
import java.util.Map;

/** Attempt-local table counters shared by source mailboxes and acknowledged Sink commits. */
public final class TableMetrics {
    private final Map<TableId, Counters> tables = new LinkedHashMap<>();

    /** Registers a table before any asynchronous source or sink callbacks. */
    public synchronized void register(TableId table) { tables.computeIfAbsent(table, ignored -> new Counters()); }

    /** Records the complete split count for one table, including an empty table. */
    public synchronized void planned(TableId table, int count) {
        if (count < 0) throw new IllegalArgumentException("Negative planned split count");
        Counters value = counters(table);
        value.planned = count;
    }

    /** Counts a row only after it is emitted from the reader into its output. */
    public synchronized void read(TableId table) { counters(table).read++; }

    /** Counts a split only after its final queued row was emitted by the owning mailbox. */
    public synchronized void splitFinished(TableId table) { counters(table).completed++; }

    /** Counts input records only after their JDBC transaction commit was acknowledged. */
    public synchronized void committed(TableId table, long count) {
        if (count < 0) throw new IllegalArgumentException("Negative committed record count");
        counters(table).committed += count;
    }

    /** Records a failed read without retaining exception text, credentials or row values. */
    public synchronized void readFailed(TableId table) { counters(table).failure = "READ_FAILED"; }

    /** Records an unsuccessful or ambiguous destination transaction. */
    public synchronized void writeFailed(TableId table) { counters(table).failure = "WRITE_FAILED"; }

    /** Returns a detached, consistent observation of all counters. */
    public synchronized Map<TableId, Snapshot> snapshot() {
        Map<TableId, Snapshot> values = new LinkedHashMap<>();
        tables.forEach((table, value) -> values.put(table,
                new Snapshot(value.planned, value.completed, value.read, value.committed, value.failure)));
        return java.util.Collections.unmodifiableMap(values);
    }

    private Counters counters(TableId table) { return tables.computeIfAbsent(table, ignored -> new Counters()); }

    /** Non-secret measurement values; a null planned count means planning has not been observed. */
    public record Snapshot(Integer plannedSplits, long completedSplits, long readRows, long committedRows, String failureCode) {}

    private static final class Counters {
        private Integer planned;
        private long completed;
        private long read;
        private long committed;
        private String failure;
    }
}
''')
for role in ['Source','Sink']:
    path=C+'factory/Table'+role+'Factory.java'
    old=f'    {"Source<TableRecord, ?, ?>" if role=="Source" else "Sink<TableRecord>"} create{role}(Table{role}FactoryContext context);'
    result='Source<TableRecord, ?, ?>' if role=='Source' else 'Sink<TableRecord>'
    edit(path,old,old+f'''

    /**
     * Constructs one physical {role} for a nonempty ordered collection of resolved tables.
     *
     * @param contexts effective connection and table inputs for the shared execution
     * @return reusable {role} definition; unsupported multi-table factories reject the request
     */
    default {result} create{role}(java.util.List<Table{role}FactoryContext> contexts) {{
        if (contexts.size() != 1) throw new UnsupportedOperationException("Factory does not support multiple tables");
        return create{role}(contexts.getFirst());
    }}''')
    path=C+'factory/Table'+role+'FactoryContext.java'
    edit(path,'import java.util.Objects;', 'import java.util.Objects;\nimport io.yak.ops.connector.base.metrics.TableMetrics;')
    edit(path, '    private final ReadonlyConfig options;', '    private final ReadonlyConfig options;\n    private final TableMetrics metrics;')
    if role=='Source':
        edit(path, '''        this.options = Objects.requireNonNull(options, "options");''', '''        this(options, table, schema, new TableMetrics());
    }

    /** Creates resolved Source inputs with the physical attempt's table observations. */
    public TableSourceFactoryContext(ReadonlyConfig options, TableId table, TableSchema schema, TableMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.options = Objects.requireNonNull(options, "options");''')
    else:
        edit(path, '''        this.options = Objects.requireNonNull(options, "options");''', '''        this(options, sourceTable, sourceSchema, targetTable, targetSchema, new TableMetrics());
    }

    /** Creates resolved Sink inputs with the physical attempt's table observations. */
    public TableSinkFactoryContext(ReadonlyConfig options, TableId sourceTable, TableSchema sourceSchema,
            TableId targetTable, TableSchema targetSchema, TableMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.options = Objects.requireNonNull(options, "options");''')
    edit(path, '    public ReadonlyConfig options() {', '    /** Returns the non-secret attempt-local measurement collector. */\n    public TableMetrics metrics() { return metrics; }\n\n    public ReadonlyConfig options() {')
path=J+'source/JdbcSourceFactory.java'
start='''        FactoryUtil.validateCreation(context.options(), this);
        return new JdbcSource(
                new JdbcConnectionOptions(context.options()),
                List.of(context.table()),
                JdbcSourceOptions.configuration(context.options()));'''
edit(path,start,'''        return createSource(List.of(context));
    }

    @Override
    public Source<TableRecord, ?, ?> createSource(List<TableSourceFactoryContext> contexts) {
        if (contexts.isEmpty()) throw new IllegalArgumentException("Source needs tables");
        var first = contexts.getFirst();
        for (var context : contexts) {
            FactoryUtil.validateCreation(context.options(), this);
            if (!context.options().equals(first.options())) {
                throw new IllegalArgumentException("JDBC tables must share effective Source settings");
            }
        }
        var connection = new JdbcConnectionOptions(first.options());
        return new JdbcSource(
                new io.yak.ops.connector.jdbc.database.connection.DefaultJdbcConnectionProvider(connection),
                connection.url(), contexts.stream().map(TableSourceFactoryContext::table).toList(),
                JdbcSourceOptions.configuration(first.options()), java.util.Map.of(), first.metrics());''')
path=J+'sink/JdbcSinkFactory.java'
old='''        FactoryUtil.validateCreation(context.options(), this);
        JdbcConnectionOptions connection = new JdbcConnectionOptions(context.options());
        JdbcWriteMode mode = context.options().get(JdbcSinkOptions.WRITE_MODE);
        validate(context.options(), false);
        JdbcTableWritePlan plan = new JdbcTableWritePlan(
                context.sourceTable(),
                context.targetTable(),
                context.targetSchema(),
                mode,
                context.sourceSchema(),
                Map.of());
        return JdbcSink.builder()
                .withConnectionProvider(new DefaultJdbcConnectionProvider(connection))
                .withDialect(JdbcFactoryLoader.loadDialect(connection.url()))
                .withTablePlans(List.of(plan))
                .build();'''
new='''        return createSink(List.of(context));
    }

    @Override
    public Sink<TableRecord> createSink(List<TableSinkFactoryContext> contexts) {
        if (contexts.isEmpty()) throw new IllegalArgumentException("Sink needs table routes");
        var first = contexts.getFirst();
        JdbcConnectionOptions connection = new JdbcConnectionOptions(first.options());
        List<JdbcTableWritePlan> plans = new java.util.ArrayList<>();
        var common = new java.util.LinkedHashMap<>(first.options().getSourceMap());
        common.remove(JdbcSinkOptions.WRITE_MODE.key());
        for (var context : contexts) {
            FactoryUtil.validateCreation(context.options(), this);
            var configured = new java.util.LinkedHashMap<>(context.options().getSourceMap());
            configured.remove(JdbcSinkOptions.WRITE_MODE.key());
            if (!common.equals(configured)) throw new IllegalArgumentException("JDBC routes must share connection settings");
            validate(context.options(), false);
            plans.add(new JdbcTableWritePlan(context.sourceTable(), context.targetTable(), context.targetSchema(),
                    context.options().get(JdbcSinkOptions.WRITE_MODE), context.sourceSchema(), Map.of()));
        }
        return new JdbcSink(new DefaultJdbcConnectionProvider(connection), JdbcFactoryLoader.loadDialect(connection.url()),
                plans, io.yak.ops.connector.base.sink.writer.BatchFlushPolicy.defaults(), first.metrics());'''
edit(path,old,new)
# Source constructors carry metrics without altering checkpoint state or serialized definitions.
path=J+'source/JdbcSource.java'
edit(path,'import java.util.Objects;', 'import java.util.Objects;\nimport io.yak.ops.connector.base.metrics.TableMetrics;')
edit(path,'    private final JdbcConnectionProvider connectionProvider;', '    private final JdbcConnectionProvider connectionProvider;\n    private final TableMetrics metrics;')
edit(path,'''        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");''', '''        this(connectionProvider, jdbcUrl, tables, configuration, projections, new TableMetrics());
    }

    /** Creates a reusable bounded Source with attempt-local, non-checkpointed observations. */
    public JdbcSource(JdbcConnectionProvider connectionProvider, String jdbcUrl, List<TableId> tables,
            Configuration configuration, Map<TableId, List<String>> projections, TableMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");''')
edit(path,'new JdbcSourceReader(connectionProvider, dialect, configuration, context)', 'new JdbcSourceReader(connectionProvider, dialect, configuration, context, metrics)')
edit(path,'                restored);','                restored, metrics);')
path=J+'source/enumerator/JdbcSourceEnumerator.java'
edit(path,'import java.util.Set;', 'import java.util.Set;\nimport io.yak.ops.connector.base.metrics.TableMetrics;')
edit(path,'    private final JdbcSplitPlanner planner;', '    private final JdbcSplitPlanner planner;\n    private final TableMetrics metrics;')
edit(path,'''        this.context = Objects.requireNonNull(context, "context");''', '''        this(context, planner, tables, fingerprint, restored, new TableMetrics());
    }

    /** Creates per-table split planning with an attempt-local progress collector. */
    public JdbcSourceEnumerator(SplitEnumeratorContext<JdbcSourceSplit> context, JdbcSplitPlanner planner,
            List<TableId> tables, String fingerprint, JdbcEnumeratorState restored, TableMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.context = Objects.requireNonNull(context, "context");''')
edit(path,'            if (failure != null) {\n                throw new IllegalStateException("JDBC table split planning failed", failure);', '            if (failure != null) {\n                metrics.readFailed(table);\n                throw new IllegalStateException("JDBC table split planning failed", failure);')
edit(path,'            pending.addAll(planned);', '            metrics.planned(table, planned.size());\n            pending.addAll(planned);')
path=J+'source/reader/JdbcSourceReader.java'
edit(path,'import java.util.Objects;', 'import java.util.Objects;\nimport io.yak.ops.connector.base.metrics.TableMetrics;')
edit(path,'''    public JdbcSourceReader(
            JdbcConnectionOptions connection,''', '''    private final TableMetrics metrics;

    public JdbcSourceReader(
            JdbcConnectionOptions connection,''')
edit(path,'''        super(
                () -> new JdbcSourceSplitReader(connections, dialect, configuration),
                new JdbcRecordEmitter(),
                Objects.requireNonNull(context, "context"));''', '''        this(connections, dialect, configuration, context, new TableMetrics());
    }

    /** Creates one mailbox-owned reader with shared table observations. */
    public JdbcSourceReader(JdbcConnectionProvider connections, JdbcDialect dialect, Configuration configuration,
            SourceReaderContext context, TableMetrics metrics) {
        super(() -> new JdbcSourceSplitReader(connections, dialect, configuration, metrics),
                new JdbcRecordEmitter(metrics), Objects.requireNonNull(context, "context"));
        this.metrics = Objects.requireNonNull(metrics, "metrics");''')
edit(path,'''        context.reportCompletedSplits(finished.size());''', '''        finished.values().forEach(state -> metrics.splitFinished(state.toCheckpointSplit().tableId()));
        context.reportCompletedSplits(finished.size());''')
path=J+'source/reader/JdbcRecordEmitter.java'
edit(path,'import io.yak.ops.core.data.TableRecord;', 'import io.yak.ops.core.data.TableRecord;\nimport io.yak.ops.connector.base.metrics.TableMetrics;')
edit(path,'''    @Override
    public void emitRecord(''', '''    private final TableMetrics metrics;

    public JdbcRecordEmitter() { this(new TableMetrics()); }

    public JdbcRecordEmitter(TableMetrics metrics) { this.metrics = java.util.Objects.requireNonNull(metrics); }

    @Override
    public void emitRecord(''')
edit(path,'        splitState.onRecordEmitted(element.lastKey());', '        splitState.onRecordEmitted(element.lastKey());\n        metrics.read(element.record().tableId());')
path=J+'source/reader/JdbcSourceSplitReader.java'
edit(path,'import org.slf4j.MDC;', 'import org.slf4j.MDC;\nimport io.yak.ops.connector.base.metrics.TableMetrics;')
edit(path,'    private final JdbcConnectionProvider connections;', '    private final JdbcConnectionProvider connections;\n    private final TableMetrics metrics;')
edit(path,'''        this.connections = Objects.requireNonNull(connections, "connections");''', '''        this(connections, dialect, configuration, new TableMetrics());
    }

    /** Binds blocking fetch failures to their active table without retaining raw diagnostics. */
    public JdbcSourceSplitReader(JdbcConnectionProvider connections, JdbcDialect dialect, Configuration configuration,
            TableMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.connections = Objects.requireNonNull(connections, "connections");''')
edit(path,'''    public RecordsWithSplitIds<JdbcRecordAndPosition> fetch() throws Exception {
        if (closed || cancellationRequested) {''', '''    public RecordsWithSplitIds<JdbcRecordAndPosition> fetch() throws Exception {
        try {
            return fetchRecords();
        } catch (Exception failure) {
            if (active != null && !cancellationRequested) metrics.readFailed(active.tableId());
            throw failure;
        }
    }

    private RecordsWithSplitIds<JdbcRecordAndPosition> fetchRecords() throws Exception {
        if (closed || cancellationRequested) {''')
path=J+'sink/JdbcSink.java'
edit(path,'import java.util.Set;', 'import java.util.Set;\nimport io.yak.ops.connector.base.metrics.TableMetrics;')
edit(path,'    private final BatchFlushPolicy batchPolicy;', '    private final BatchFlushPolicy batchPolicy;\n    private final TableMetrics metrics;')
edit(path,'''        this.connections = Objects.requireNonNull(connections, "connections");''', '''        this(connections, dialect, plans, batchPolicy, new TableMetrics());
    }

    /** Creates routes whose measurements count only acknowledged transaction commits. */
    public JdbcSink(JdbcConnectionProvider connections, JdbcDialect dialect, List<JdbcTableWritePlan> plans,
            BatchFlushPolicy batchPolicy, TableMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.connections = Objects.requireNonNull(connections, "connections");''')
edit(path,'new JdbcOutputFormat(connections, dialect, plans)', 'new JdbcOutputFormat(connections, dialect, plans, metrics)')
path=J+'internal/JdbcOutputFormat.java'
edit(path,'import java.util.Objects;', 'import java.util.Objects;\nimport java.util.Map;\nimport java.util.LinkedHashMap;\nimport io.yak.ops.core.data.TableId;\nimport io.yak.ops.connector.base.metrics.TableMetrics;')
edit(path,'    private final Connection connection;', '    private final Connection connection;\n    private final TableMetrics metrics;\n    private final Map<TableId, Long> pendingTableRows = new LinkedHashMap<>();')
edit(path,'''        Objects.requireNonNull(connections, "connections");''', '''        this(connections, dialect, plans, new TableMetrics());
    }

    /** Opens the transaction shared by all routes and reports acknowledged commits by source table. */
    public JdbcOutputFormat(JdbcConnectionProvider connections, JdbcDialect dialect, List<JdbcTableWritePlan> plans,
            TableMetrics metrics) throws SQLException {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(connections, "connections");''')
edit(path,'''        executor.addToBatch(record);''', '''        try {
            executor.addToBatch(record);
            pendingTableRows.merge(record.tableId(), 1L, Long::sum);
        } catch (RuntimeException failure) {
            metrics.writeFailed(record.tableId());
            throw failure;
        }''')
edit(path,'''            committedRecords += batchRows;''', '''            pendingTableRows.forEach(metrics::committed);
            pendingTableRows.clear();
            committedRecords += batchRows;''')
edit(path,'''            failed = true;
            try {''', '''            failed = true;
            if (!cancelled) pendingTableRows.keySet().forEach(metrics::writeFailed);
            try {''')
print('Multi-table factories and JDBC table measurements applied')
