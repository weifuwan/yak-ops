package io.yak.ops.connector.jdbc.source.split;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.TableSchema;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Stable identity of a JDBC table's ordered read columns, resolved types, and split-key contract.
 *
 * <p>The digest contains neither data values nor connection credentials. It detects schema
 * incompatibility between split planning and a resumed database query, not concurrent row updates.
 */
public final class JdbcSchemaFingerprint {

    private JdbcSchemaFingerprint() {}

    /**
     * Hashes the read projection and split-key identity into a stable SHA-256 fingerprint.
     *
     * <p>Resolved column types and order are part of the digest; data values, credentials,
     * and transient JDBC connection state are not. It validates schema compatibility, not
     * a transactional view of concurrent database updates.
     *
     * @param table physical table identity
     * @param schema ordered and fully resolved JDBC projection
     * @param splitColumn optional numeric progress key
     * @return lowercase SHA-256 digest for the frozen split contract
     */
    public static String of(TableId table, TableSchema schema, String splitColumn) {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(schema, "schema");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            append(digest, table.catalog());
            append(digest, table.schema());
            append(digest, table.table());
            append(digest, splitColumn);
            append(digest, Integer.toString(schema.columnCount()));
            for (Column column : schema.columns()) {
                append(digest, column.name());
                append(digest, column.dataType().asSerializableString());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    /** Updates the digest with an unambiguous nullable UTF-8 field. */
    private static void append(MessageDigest digest, String value) {
        if (value == null) {
            digest.update((byte) 0);
        } else {
            digest.update((byte) 1);
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            digest.update(new byte[] {
                (byte) (bytes.length >>> 24),
                (byte) (bytes.length >>> 16),
                (byte) (bytes.length >>> 8),
                (byte) bytes.length
            });
            digest.update(bytes);
        }
    }
}
