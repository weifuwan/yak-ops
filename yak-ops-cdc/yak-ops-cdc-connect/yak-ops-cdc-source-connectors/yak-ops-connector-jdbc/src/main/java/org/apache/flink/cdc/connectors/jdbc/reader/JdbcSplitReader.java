package org.apache.flink.cdc.connectors.jdbc.reader;

import org.apache.flink.cdc.common.data.DateData;
import org.apache.flink.cdc.common.data.DecimalData;
import org.apache.flink.cdc.common.data.GenericRecordData;
import org.apache.flink.cdc.common.data.TimeData;
import org.apache.flink.cdc.common.data.TimestampData;
import org.apache.flink.cdc.common.data.binary.BinaryStringData;
import org.apache.flink.cdc.common.event.CreateTableEvent;
import org.apache.flink.cdc.common.event.DataChangeEvent;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.common.types.DataType;
import org.apache.flink.cdc.common.types.DataTypeRoot;
import org.apache.flink.cdc.common.types.DecimalType;
import org.apache.flink.cdc.connectors.jdbc.config.JdbcSourceConfig;
import org.apache.flink.cdc.connectors.jdbc.connection.JdbcConnectionFactory;
import org.apache.flink.cdc.connectors.jdbc.dialect.JdbcDialect;
import org.apache.flink.cdc.connectors.jdbc.split.JdbcSourceSplit;
import org.apache.flink.connector.base.source.reader.RecordsWithSplitIds;
import org.apache.flink.connector.base.source.reader.splitreader.SplitReader;
import org.apache.flink.connector.base.source.reader.splitreader.SplitsAddition;
import org.apache.flink.connector.base.source.reader.splitreader.SplitsChange;

import java.io.IOException;
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
import java.util.List;
import java.util.Locale;
import java.util.Queue;

/** Executes one JDBC split at a time and returns bounded batches to Flink's split fetcher. */
public final class JdbcSplitReader implements SplitReader<JdbcRecord, JdbcSourceSplit> {

    private final JdbcSourceConfig config;
    private final Queue<JdbcSourceSplit> pendingSplits = new ArrayDeque<>();

    private JdbcSourceSplit currentSplit;
    private volatile Connection connection;
    private volatile PreparedStatement statement;
    private ResultSet resultSet;
    private boolean cursorTransaction;
    private boolean schemaEmitted;
    private int keyIndex = -1;
    private volatile boolean closed;
    private volatile boolean wakeupRequested;

    public JdbcSplitReader(JdbcSourceConfig config) {
        this.config = config;
    }

    @Override
    public RecordsWithSplitIds<JdbcRecord> fetch() throws IOException {
        if (closed) {
            return JdbcRecords.empty();
        }

        try {
            if (currentSplit == null) {
                currentSplit = pendingSplits.poll();
                if (currentSplit == null) {
                    return JdbcRecords.empty();
                }
                openSplit(currentSplit);
            }

            List<JdbcRecord> records = new ArrayList<>(config.getFetchSize() + 1);
            if (!schemaEmitted) {
                records.add(new JdbcRecord(
                        new CreateTableEvent(currentSplit.getTableId(), currentSplit.getSchema()),
                        null));
                schemaEmitted = true;
            }

            while (records.size() < config.getFetchSize() + 1 && resultSet.next()) {
                GenericRecordData row = convert(resultSet, currentSplit.getSchema());
                BigDecimal lastReadValue =
                        keyIndex < 0 ? null : resultSet.getBigDecimal(keyIndex);
                records.add(
                        new JdbcRecord(
                                DataChangeEvent.insertEvent(currentSplit.getTableId(), row),
                                lastReadValue));
            }

            if (!records.isEmpty()) {
                return JdbcRecords.forRecords(currentSplit.splitId(), records);
            }

            String finishedSplitId = currentSplit.splitId();
            closeCurrentSplit(true);
            return JdbcRecords.finished(finishedSplitId);
        } catch (Exception e) {
            String failedSplitId =
                    currentSplit == null ? "unknown" : currentSplit.splitId();
            try {
                closeCurrentSplit(false);
            } catch (SQLException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            if (closed || wakeupRequested) {
                return JdbcRecords.empty();
            }
            throw new IOException("Failed to read JDBC split " + failedSplitId, e);
        }
    }

    private void openSplit(JdbcSourceSplit split) throws SQLException {
        connection = JdbcConnectionFactory.openConnection(config);
        String product =
                connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        cursorTransaction = product.contains("mysql") || product.contains("postgres");
        if (cursorTransaction) {
            connection.setAutoCommit(false);
        }

        JdbcDialect dialect = JdbcDialect.create(connection.getMetaData());
        String currentSchema = null;
        try {
            currentSchema = connection.getSchema();
        } catch (SQLException ignored) {
            // Drivers without schema support use the catalog/table form.
        }

        String columns =
                String.join(
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

        statement =
                connection.prepareStatement(
                        sql.toString(),
                        ResultSet.TYPE_FORWARD_ONLY,
                        ResultSet.CONCUR_READ_ONLY);
        statement.setFetchSize(config.getFetchSize());
        for (int i = 0; i < parameters.size(); i++) {
            statement.setBigDecimal(i + 1, parameters.get(i));
        }
        resultSet = statement.executeQuery();
        keyIndex =
                split.getSplitColumn() == null
                        ? -1
                        : split.getSchema().getColumnNames().indexOf(split.getSplitColumn()) + 1;
        if (split.getSplitColumn() != null && keyIndex < 1) {
            throw new SQLException(
                    "Split column does not exist in table schema: " + split.getSplitColumn());
        }
        schemaEmitted = false;
    }

    private void closeCurrentSplit(boolean commit) throws SQLException {
        SQLException failure = null;
        if (resultSet != null) {
            try {
                resultSet.close();
            } catch (SQLException e) {
                failure = e;
            }
        }
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }

        Connection activeConnection = connection;
        if (activeConnection != null && cursorTransaction) {
            try {
                if (commit && failure == null) {
                    activeConnection.commit();
                } else {
                    activeConnection.rollback();
                }
            } catch (SQLException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (activeConnection != null) {
            try {
                activeConnection.close();
            } catch (SQLException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }

        resultSet = null;
        statement = null;
        connection = null;
        currentSplit = null;
        cursorTransaction = false;
        schemaEmitted = false;
        keyIndex = -1;
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public void handleSplitsChanges(SplitsChange<JdbcSourceSplit> splitsChanges) {
        if (!(splitsChanges instanceof SplitsAddition)) {
            throw new UnsupportedOperationException(
                    "Unsupported JDBC split change: " + splitsChanges.getClass().getName());
        }
        pendingSplits.addAll(splitsChanges.splits());
    }

    @Override
    public void wakeUp() {
        wakeupRequested = true;
        PreparedStatement activeStatement = statement;
        if (activeStatement != null) {
            try {
                activeStatement.cancel();
            } catch (SQLException ignored) {
                // Closing the split reader will release the connection after the fetcher exits.
            }
        } else {
            Connection activeConnection = connection;
            if (activeConnection != null) {
                try {
                    activeConnection.close();
                } catch (SQLException ignored) {
                    // The fetcher will close the connection as part of split cleanup.
                }
            }
        }
    }

    @Override
    public void close() throws Exception {
        closed = true;
        pendingSplits.clear();
        closeCurrentSplit(false);
    }

    private static GenericRecordData convert(ResultSet resultSet, Schema schema)
            throws SQLException {
        GenericRecordData row = new GenericRecordData(schema.getColumnCount());
        for (int i = 0; i < schema.getColumnCount(); i++) {
            DataType columnType = schema.getColumnDataTypes().get(i);
            if (columnType.getTypeRoot() == DataTypeRoot.CHAR
                    || columnType.getTypeRoot() == DataTypeRoot.VARCHAR) {
                String value = resultSet.getString(i + 1);
                row.setField(i, value == null ? null : BinaryStringData.fromString(value));
                continue;
            }
            Object value = resultSet.getObject(i + 1);
            row.setField(i, value == null ? null : convertValue(value, columnType));
        }
        return row;
    }

    private static Object convertValue(Object value, DataType type) throws SQLException {
        DataTypeRoot root = type.getTypeRoot();
        switch (root) {
            case BOOLEAN:
                if (value instanceof Boolean) {
                    return value;
                }
                if (value instanceof Number) {
                    return ((Number) value).intValue() != 0;
                }
                if (value instanceof byte[]) {
                    byte[] bytes = (byte[]) value;
                    return bytes.length > 0 && bytes[0] != 0;
                }
                return "1".equals(value.toString()) || Boolean.parseBoolean(value.toString());
            case TINYINT:
                return ((Number) value).byteValue();
            case SMALLINT:
                return ((Number) value).shortValue();
            case INTEGER:
                return ((Number) value).intValue();
            case BIGINT:
                return ((Number) value).longValue();
            case FLOAT:
                return ((Number) value).floatValue();
            case DOUBLE:
                return ((Number) value).doubleValue();
            case DECIMAL:
                DecimalType decimalType = (DecimalType) type;
                BigDecimal decimal =
                        value instanceof BigDecimal
                                ? (BigDecimal) value
                                : new BigDecimal(value.toString());
                DecimalData decimalData =
                        DecimalData.fromBigDecimal(
                                decimal, decimalType.getPrecision(), decimalType.getScale());
                if (decimalData == null) {
                    throw new SQLException("JDBC decimal exceeds declared precision");
                }
                return decimalData;
            case DATE:
                if (value instanceof Date) {
                    return DateData.fromLocalDate(((Date) value).toLocalDate());
                }
                if (value instanceof LocalDate) {
                    return DateData.fromLocalDate((LocalDate) value);
                }
                return DateData.fromIsoLocalDateString(value.toString());
            case TIME_WITHOUT_TIME_ZONE:
                if (value instanceof Time) {
                    return TimeData.fromLocalTime(((Time) value).toLocalTime());
                }
                if (value instanceof LocalTime) {
                    return TimeData.fromLocalTime((LocalTime) value);
                }
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
                if (value instanceof byte[]) {
                    return value;
                }
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
                if (value instanceof java.sql.Clob) {
                    java.sql.Clob clob = (java.sql.Clob) value;
                    try {
                        return BinaryStringData.fromString(
                                clob.getSubString(1, (int) clob.length()));
                    } finally {
                        clob.free();
                    }
                }
                if (value instanceof java.sql.SQLXML) {
                    java.sql.SQLXML xml = (java.sql.SQLXML) value;
                    try {
                        return BinaryStringData.fromString(xml.getString());
                    } finally {
                        xml.free();
                    }
                }
                return BinaryStringData.fromString(value.toString());
        }
    }
}
