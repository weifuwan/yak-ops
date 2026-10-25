package io.yak.ops.connector.jdbc.xa;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;

/** XIDs must be stable through checkpoint serialization and globally distinct across attempts. */
class MySqlXaXidSerializerTest {

    private final MySqlXaXidSerializer serializer = new MySqlXaXidSerializer();

    @Test
    void retainsTheExactPreparedTransactionIdentity() throws Exception {
        MySqlXaXid xid = MySqlXaXid.next("107715469437022208", 0, 17);
        MySqlXaXid restored = serializer.deserialize(serializer.getVersion(), serializer.serialize(xid));
        assertEquals(xid, restored);
        assertEquals(xid.hashCode(), restored.hashCode());
        assertTrue(xid.belongsTo("107715469437022208"));
        assertTrue(!xid.belongsTo("another-job"));
        assertArrayEquals(xid.getGlobalTransactionId(), restored.getGlobalTransactionId());
    }

    @Test
    void newAttemptsCannotAccidentallyReuseAPreparedXid() {
        MySqlXaXid first = MySqlXaXid.next("job-1", 0, 7);
        MySqlXaXid second = MySqlXaXid.next("job-1", 0, 7);
        assertNotEquals(first, second);
        assertNotEquals(first, MySqlXaXid.next("job-2", 0, 7));
        assertThrows(IllegalArgumentException.class, () -> MySqlXaXid.next("wrong/owner", 0, 7));
    }

    @Test
    void rejectsCorruptionAndUnknownStateVersions() throws Exception {
        MySqlXaXid xid = MySqlXaXid.next("job-1", 0, 5);
        byte[] bytes = serializer.serialize(xid);
        assertThrows(IOException.class, () -> serializer.deserialize(99, bytes));
        assertThrows(IOException.class, () -> serializer.deserialize(1, java.util.Arrays.copyOf(bytes, 8)));
        byte[] padded = java.util.Arrays.copyOf(bytes, bytes.length + 1);
        assertThrows(IOException.class, () -> serializer.deserialize(1, padded));
    }
}
