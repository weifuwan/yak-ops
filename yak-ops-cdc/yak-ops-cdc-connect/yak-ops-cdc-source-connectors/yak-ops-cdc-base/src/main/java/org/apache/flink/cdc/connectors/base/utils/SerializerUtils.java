package org.apache.flink.cdc.connectors.base.utils;

import io.debezium.DebeziumException;
import io.debezium.relational.TableId;
import io.debezium.util.HexConverter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/** Utils for serialization and deserialization. */
public class SerializerUtils {

    private SerializerUtils() {}

    public static String rowToSerializedString(Object[] splitBoundary) {
        try (final ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(splitBoundary);
            return HexConverter.convertToHexString(bos.toByteArray());
        } catch (IOException e) {
            throw new DebeziumException(
                    String.format("Cannot serialize split boundary information %s", splitBoundary));
        }
    }

    public static String rowToSerializedString(Object splitBoundary) {
        try (final ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(splitBoundary);
            return HexConverter.convertToHexString(bos.toByteArray());
        } catch (IOException e) {
            throw new DebeziumException(
                    String.format("Cannot serialize split boundary information %s", splitBoundary));
        }
    }

    public static Object[] serializedStringToRow(String serialized) {
        try (final ByteArrayInputStream bis =
                        new ByteArrayInputStream(HexConverter.convertFromHex(serialized));
                ObjectInputStream ois = new ObjectInputStream(bis)) {
            return (Object[]) ois.readObject();
        } catch (Exception e) {
            throw new DebeziumException(
                    String.format(
                            "Failed to deserialize split boundary with value '%s'", serialized),
                    e);
        }
    }

    public static Object serializedStringToObject(String serialized) {
        try (final ByteArrayInputStream bis =
                        new ByteArrayInputStream(HexConverter.convertFromHex(serialized));
                ObjectInputStream ois = new ObjectInputStream(bis)) {
            return ois.readObject();
        } catch (Exception e) {
            throw new DebeziumException(
                    String.format(
                            "Failed to deserialize split boundary with value '%s'", serialized),
                    e);
        }
    }

    /**
     * Check whether the catalog should be used before the schema.
     *
     * @param tableId the table id
     * @return false if catalog is not defined but the schema is defined; otherwise return true
     */
    public static boolean shouldUseCatalogBeforeSchema(TableId tableId) {
        return !(tableId.catalog() == null && tableId.schema() != null);
    }
}
