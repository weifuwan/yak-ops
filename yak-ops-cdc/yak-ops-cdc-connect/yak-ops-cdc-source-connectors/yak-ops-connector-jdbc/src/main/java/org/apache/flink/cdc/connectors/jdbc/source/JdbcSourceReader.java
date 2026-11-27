package org.apache.flink.cdc.connectors.jdbc.source;

import org.apache.flink.api.connector.source.ReaderOutput;
import org.apache.flink.api.connector.source.SourceEvent;
import org.apache.flink.api.connector.source.SourceReader;
import org.apache.flink.cdc.common.data.DateData;
import org.apache.flink.cdc.common.data.DecimalData;
import org.apache.flink.cdc.common.data.GenericRecordData;
import org.apache.flink.cdc.common.data.TimeData;
import org.apache.flink.cdc.common.data.TimestampData;
import org.apache.flink.cdc.common.data.binary.BinaryStringData;
import org.apache.flink.cdc.common.event.CreateTableEvent;
import org.apache.flink.cdc.common.event.DataChangeEvent;
import org.apache.flink.cdc.common.event.Event;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.common.types.DataType;
import org.apache.flink.cdc.common.types.DataTypeRoot;
import org.apache.flink.cdc.common.types.DecimalType;
import org.apache.flink.core.io.InputStatus;

import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Asynchronous bounded JDBC reader. pollNext only drains prefetched events and never performs JDBC I/O. */
public final class JdbcSourceReader implements SourceReader<Event, JdbcSourceSplit> {

    private final JdbcSourceConfig config;
    private final Object lock = new Object();
    private final Queue<JdbcSourceSplit> pending = new ArrayDeque<>();
    private final ArrayBlockingQueue<ReaderItem> outputQueue;
    private final Set<TableId> emittedSchemas = new HashSet<>();

    private volatile CompletableFuture<Void> availability = new CompletableFuture<>();
    private volatile Connection activeConnection;
    private volatile Throwable workerFailure;
    private volatile boolean running;
    private volatile boolean noMoreSplits;
    private volatile boolean workerFinished;
    private JdbcSourceSplit activeSplit;
    private Thread fetcher;

    public JdbcSourceReader(JdbcSourceConfig config) {
        this.config = config;
        this.outputQueue = new ArrayBlockingQueue<>(Math.max(64, config.getFetchSize() * 2));
    }

    @Override
    public void start() {
        running = true;
        fetcher = new Thread(this::fetchLoop, "yak-jdbc-source-reader");
        fetcher.setDaemon(true);
        fetcher.start();
    }

    @Override
    public InputStatus pollNext(ReaderOutput<Event> output) throws Exception {
        if (workerFailure != null) {
            Throwable failure = workerFailure;
            if (failure instanceof Exception) throw (Exception) failure;
            if (failure instanceof Error) throw (Error) failure;
            throw new RuntimeException("JDBC source reader failed", failure);
        }

        ReaderItem item = outputQueue.poll();
        if (item == null) {
            if (isFinished()) return InputStatus.END_OF_INPUT;
            resetAvailability();
            return availability.isDone()
                    ? InputStatus.MORE_AVAILABLE
                    : InputStatus.NOTHING_AVAILABLE;
        }

        if (item.finished) {
            synchronized (lock) {
                if (activeSplit != null && activeSplit.splitId().equals(item.splitId)) {
                    activeSplit = null;
                    lock.notifyAll();
                }
            }
        } else {
            output.collect(item.event);
            if (item.lastReadKey != null) {
                synchronized (lock) {
                    if (activeSplit != null && activeSplit.splitId().equals(item.splitId)) {
                        activeSplit = activeSplit.withLastReadValue(item.lastReadKey);
                    }
                }
            }
        }

        if (!outputQueue.isEmpty()) return InputStatus.MORE_AVAILABLE;
        if (isFinished()) return InputStatus.END_OF_INPUT;
        resetAvailability();
        return availability.isDone()
                ? InputStatus.MORE_AVAILABLE
                : InputStatus.NOTHING_AVAILABLE;
    }

    private void resetAvailability() {
        CompletableFuture<Void> next = new CompletableFuture<>();
        availability = next;
        if (!outputQueue.isEmpty() || workerFinished || workerFailure != null) {
            next.complete(null);
        }
    }

    private boolean isFinished() {
        synchronized (lock) {
            return workerFinished && activeSplit == null && pending.isEmpty() && outputQueue.isEmpty();
        }
    }

    private void signalAvailable() {
        availability.complete(null);
    }

    private void fetchLoop() {
        try {
            while (running) {
                JdbcSourceSplit split;
                synchronized (lock) {
                    while (running && pending.isEmpty() && !noMoreSplits) {
                        lock.wait();
                    }
                    if (!running) return;
                    if (pending.isEmpty() && noMoreSplits) {
                        workerFinished = true;
                        signalAvailable();
                        return;
                    }
                    split = pending.remove();
                    activeSplit = split;
                }

                readSplit(split);
                put(ReaderItem.finished(split.splitId()));
                synchronized (lock) {
                    while (running && activeSplit != null) {
                        lock.wait();
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (running) {
                workerFailure = e;
                workerFinished = true;
                signalAvailable();
            }
        } catch (Throwable e) {
            workerFailure = e;
            workerFinished = true;
            signalAvailable();
        }
    }

    private void readSplit(JdbcSourceSplit split) throws Exception {
        if (emittedSchemas.add(split.getTableId())) {
            put(ReaderItem.event(
                    split.splitId(),
                    new CreateTableEvent(split.getTableId(), split.getSchema()),
                    null));
        }

        Connection connection = JdbcCatalog.openConnection(config);
        activeConnection = connection;
        try (Connection ignored = connection) {
            connection.setAutoCommit(false);
            JdbcDialect dialect = JdbcDialect.create(connection.getMetaData());
            String currentSchema = null;
            try {
                currentSchema = connection.getSchema();
            } catch (SQLException ignoredSchema) {
                // Drivers without schema support use the catalog/table form.
            }
            String columns = String.join(
                    ", ",
                    split.getSchema().getColumnNames().stream()
                            .map(dialect::quoteIdentifier)
                            .toArray(String[]::new));
            StringBuilder sql =
                    new StringBuilder("SELECT ")
                            .append(columns)
                            .append(" FROM ")
                            .append(
                                    dialect.qualifiedTable(
                                            split.getTableId(), connection.getCatalog(), currentSchema));

            List<BigDecimal> parameters = new ArrayList<>();
            List<String> predicates = new ArrayList<>();
            if (split.getSplitColumn() != null) {
                String key = dialect.quoteIdentifier(split.getSplitColumn());
                if (split.getLowerBound() != null) {
                    predicates.add(key + " >= ?");
                    parameters.add(split.getLowerBound());
                }
                if (split.getUpperBound() != null) {
                    predicates.add(key + (split.isUpperInclusive() ? " <= ?" : " < ?"));
                    parameters.add(split.getUpperBound());
                }
                if (split.getLastReadValue() != null) {
                    predicates.add(key + " > ?");
                    parameters.add(split.getLastReadValue());
                }
                if (!predicates.isEmpty()) {
                    sql.append(" WHERE ").append(String.join(" AND ", predicates));
                }
                sql.append(" ORDER BY ").append(key);
            }

            try (PreparedStatement statement =
                            connection.prepareStatement(
                                    sql.toString(),
                                    ResultSet.TYPE_FORWARD_ONLY,
                                    ResultSet.CONCUR_READ_ONLY)) {
                statement.setFetchSize(config.getFetchSize());
                for (int i = 0; i < parameters.size(); i++) {
                    statement.setBigDecimal(i + 1, parameters.get(i));
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    int keyIndex =
                            split.getSplitColumn() == null
                                    ? -1
                                    : split.getSchema().getColumnNames().indexOf(split.getSplitColumn()) + 1;
                    while (running && resultSet.next()) {
                        GenericRecordData row = convert(resultSet, split.getSchema());
                        BigDecimal key =
                                keyIndex < 0 ? null : resultSet.getBigDecimal(keyIndex);
                        put(ReaderItem.event(
                                split.splitId(),
                                DataChangeEvent.insertEvent(split.getTableId(), row),
                                key));
                    }
                }
            }
            connection.commit();
        } catch (Exception e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            throw e;
        } finally {
            activeConnection = null;
        }
    }

    private void put(ReaderItem item) throws InterruptedException {
        while (running) {
            if (outputQueue.offer(item, 100, TimeUnit.MILLISECONDS)) {
                signalAvailable();
                return;
            }
        }
        throw new InterruptedException("JDBC source reader is closing");
    }

    private static GenericRecordData convert(ResultSet resultSet, Schema schema) throws SQLException {
        GenericRecordData row = new GenericRecordData(schema.getColumnCount());
        for (int i = 0; i < schema.getColumnCount(); i++) {
            Object value = resultSet.getObject(i + 1);
            row.setField(i, value == null ? null : convertValue(value, schema.getColumnDataTypes().get(i)));
        }
        return row;
    }

    private static Object convertValue(Object value, DataType type) throws SQLException {
        DataTypeRoot root = type.getTypeRoot();
        switch (root) {
            case BOOLEAN:
                return value instanceof Boolean ? value : ((Number) value).intValue() != 0;
            case TINYINT: return ((Number) value).byteValue();
            case SMALLINT: return ((Number) value).shortValue();
            case INTEGER: return ((Number) value).intValue();
            case BIGINT: return ((Number) value).longValue();
            case FLOAT: return ((Number) value).floatValue();
            case DOUBLE: return ((Number) value).doubleValue();
            case DECIMAL:
                DecimalType decimalType = (DecimalType) type;
                BigDecimal decimal =
                        value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(value.toString());
                DecimalData decimalData =
                        DecimalData.fromBigDecimal(
                                decimal, decimalType.getPrecision(), decimalType.getScale());
                if (decimalData == null) {
                    throw new SQLException("JDBC decimal exceeds declared precision");
                }
                return decimalData;
            case DATE:
                if (value instanceof Date) return DateData.fromLocalDate(((Date) value).toLocalDate());
                if (value instanceof LocalDate) return DateData.fromLocalDate((LocalDate) value);
                return DateData.fromIsoLocalDateString(value.toString());
            case TIME_WITHOUT_TIME_ZONE:
                if (value instanceof Time) return TimeData.fromLocalTime(((Time) value).toLocalTime());
                if (value instanceof LocalTime) return TimeData.fromLocalTime((LocalTime) value);
                return TimeData.fromIsoLocalTimeString(value.toString());
            case TIMESTAMP_WITHOUT_TIME_ZONE:
                if (value instanceof Timestamp) {
                    return TimestampData.fromLocalDateTime(((Timestamp) value).toLocalDateTime());
                }
                if (value instanceof LocalDateTime) {
                    return TimestampData.fromLocalDateTime((LocalDateTime) value);
                }
                return TimestampData.fromLocalDateTime(
                        Timestamp.valueOf(value.toString()).toLocalDateTime());
            case BINARY:
            case VARBINARY:
                if (value instanceof byte[]) return value;
                if (value instanceof Blob) {
                    Blob blob = (Blob) value;
                    try {
                        return blob.getBytes(1, (int) blob.length());
                    } finally {
                        blob.free();
                    }
                }
                return value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            default:
                return BinaryStringData.fromString(value.toString());
        }
    }

    @Override
    public List<JdbcSourceSplit> snapshotState(long checkpointId) {
        synchronized (lock) {
            List<JdbcSourceSplit> state = new ArrayList<>();
            if (activeSplit != null) state.add(activeSplit);
            state.addAll(pending);
            return state;
        }
    }

    @Override
    public CompletableFuture<Void> isAvailable() {
        if (!outputQueue.isEmpty() || workerFinished || workerFailure != null) {
            return CompletableFuture.completedFuture(null);
        }
        return availability;
    }

    @Override
    public void addSplits(List<JdbcSourceSplit> splits) {
        synchronized (lock) {
            pending.addAll(splits);
            lock.notifyAll();
        }
        signalAvailable();
    }

    @Override
    public void notifyNoMoreSplits() {
        synchronized (lock) {
            noMoreSplits = true;
            lock.notifyAll();
        }
        signalAvailable();
    }

    @Override
    public void handleSourceEvents(SourceEvent sourceEvent) {}

    @Override
    public void close() throws Exception {
        running = false;
        synchronized (lock) {
            lock.notifyAll();
        }
        Connection connection = activeConnection;
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // The fetcher thread also closes the connection.
            }
        }
        if (fetcher != null) {
            fetcher.interrupt();
            fetcher.join(5000);
        }
    }

    private static final class ReaderItem {
        private final String splitId;
        private final Event event;
        private final BigDecimal lastReadKey;
        private final boolean finished;

        private ReaderItem(String splitId, Event event, BigDecimal lastReadKey, boolean finished) {
            this.splitId = splitId;
            this.event = event;
            this.lastReadKey = lastReadKey;
            this.finished = finished;
        }

        private static ReaderItem event(String splitId, Event event, BigDecimal key) {
            return new ReaderItem(splitId, event, key, false);
        }

        private static ReaderItem finished(String splitId) {
            return new ReaderItem(splitId, null, null, true);
        }
    }
}
