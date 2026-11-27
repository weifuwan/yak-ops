package org.apache.flink.cdc.connectors.jdbc.source;

import org.apache.flink.api.connector.source.InputStatus;
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
import java.util.concurrent.CompletableFuture;

/** Bounded JDBC reader. Failed in-flight splits resume from the last checkpointed key. */
public final class JdbcSourceReader implements SourceReader<Event, JdbcSourceSplit> {

    private final JdbcSourceConfig config;
    private final Queue<JdbcSourceSplit> pending = new ArrayDeque<>();
    private final Set<TableId> emittedSchemas = new HashSet<>();
    private JdbcSourceSplit current;
    private Connection connection;
    private PreparedStatement statement;
    private ResultSet resultSet;
    private boolean noMoreSplits;
    private CompletableFuture<Void> availability = new CompletableFuture<>();

    public JdbcSourceReader(JdbcSourceConfig config) {
        this.config = config;
    }

    @Override
    public void start() {}

    @Override
    public InputStatus pollNext(ReaderOutput<Event> output) throws Exception {
        int emitted = 0;
        while (emitted < config.getFetchSize()) {
            if (current == null) {
                current = pending.poll();
                if (current == null) {
                    if (noMoreSplits) return InputStatus.END_OF_INPUT;
                    if (availability.isDone()) availability = new CompletableFuture<>();
                    return emitted > 0 ? InputStatus.MORE_AVAILABLE : InputStatus.NOTHING_AVAILABLE;
                }
                openSplit(output);
            }

            if (resultSet.next()) {
                GenericRecordData row = convert(resultSet, current.getSchema());
                if (current.getSplitColumn() != null) {
                    int keyIndex = current.getSchema().getColumnNames()
                            .indexOf(current.getSplitColumn()) + 1;
                    BigDecimal key = resultSet.getBigDecimal(keyIndex);
                    if (key != null) current = current.withLastReadValue(key);
                }
                output.collect(DataChangeEvent.insertEvent(current.getTableId(), row));
                emitted++;
            } else {
                closeResources(true);
                current = null;
            }
        }
        return InputStatus.MORE_AVAILABLE;
    }

    private void openSplit(ReaderOutput<Event> output) throws Exception {
        if (emittedSchemas.add(current.getTableId())) {
            output.collect(new CreateTableEvent(current.getTableId(), current.getSchema()));
        }
        connection = JdbcCatalog.openConnection(config);
        connection.setAutoCommit(false);
        JdbcDialect dialect = JdbcDialect.create(connection.getMetaData());
        String currentSchema = null;
        try { currentSchema = connection.getSchema(); } catch (SQLException ignored) {}
        String columns = String.join(
                ", ",
                current.getSchema().getColumnNames().stream()
                        .map(dialect::quoteIdentifier).toArray(String[]::new));
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(columns)
                .append(" FROM ")
                .append(dialect.qualifiedTable(current.getTableId(), connection.getCatalog(), currentSchema));

        List<BigDecimal> values = new ArrayList<>();
        List<String> predicates = new ArrayList<>();
        if (current.getSplitColumn() != null) {
            String key = dialect.quoteIdentifier(current.getSplitColumn());
            if (current.getLowerBound() != null) {
                predicates.add(key + " >= ?");
                values.add(current.getLowerBound());
            }
            if (current.getUpperBound() != null) {
                predicates.add(key + (current.isUpperInclusive() ? " <= ?" : " < ?"));
                values.add(current.getUpperBound());
            }
            if (current.getLastReadValue() != null) {
                predicates.add(key + " > ?");
                values.add(current.getLastReadValue());
            }
            sql.append(" WHERE ").append(String.join(" AND ", predicates));
            sql.append(" ORDER BY ").append(key);
        }
        statement = connection.prepareStatement(
                sql.toString(), ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        statement.setFetchSize(config.getFetchSize());
        for (int i = 0; i < values.size(); i++) statement.setBigDecimal(i + 1, values.get(i));
        resultSet = statement.executeQuery();
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
                BigDecimal decimal = value instanceof BigDecimal
                        ? (BigDecimal) value : new BigDecimal(value.toString());
                DecimalData decimalData = DecimalData.fromBigDecimal(
                        decimal, decimalType.getPrecision(), decimalType.getScale());
                if (decimalData == null) throw new SQLException("JDBC decimal exceeds declared precision");
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
                    try { return blob.getBytes(1, (int) blob.length()); }
                    finally { blob.free(); }
                }
                return value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            default:
                return BinaryStringData.fromString(value.toString());
        }
    }

    private void closeResources(boolean commit) throws SQLException {
        SQLException failure = null;
        if (resultSet != null) {
            try { resultSet.close(); } catch (SQLException e) { failure = e; }
            resultSet = null;
        }
        if (statement != null) {
            try { statement.close(); } catch (SQLException e) {
                if (failure == null) failure = e; else failure.addSuppressed(e);
            }
            statement = null;
        }
        if (connection != null) {
            try {
                if (commit) connection.commit(); else connection.rollback();
            } catch (SQLException e) {
                if (failure == null) failure = e; else failure.addSuppressed(e);
            }
            try { connection.close(); } catch (SQLException e) {
                if (failure == null) failure = e; else failure.addSuppressed(e);
            }
            connection = null;
        }
        if (failure != null) throw failure;
    }

    @Override
    public List<JdbcSourceSplit> snapshotState(long checkpointId) {
        List<JdbcSourceSplit> state = new ArrayList<>();
        if (current != null) state.add(current);
        state.addAll(pending);
        return state;
    }

    @Override
    public CompletableFuture<Void> isAvailable() {
        if (current != null || !pending.isEmpty() || noMoreSplits) {
            return CompletableFuture.completedFuture(null);
        }
        return availability;
    }

    @Override
    public void addSplits(List<JdbcSourceSplit> splits) {
        pending.addAll(splits);
        availability.complete(null);
    }

    @Override
    public void notifyNoMoreSplits() {
        noMoreSplits = true;
        availability.complete(null);
    }

    @Override
    public void handleSourceEvents(SourceEvent sourceEvent) {}

    @Override
    public void close() throws Exception {
        closeResources(false);
    }
}
