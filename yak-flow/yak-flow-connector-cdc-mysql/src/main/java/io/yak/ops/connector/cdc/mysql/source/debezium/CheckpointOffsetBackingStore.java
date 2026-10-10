package io.yak.ops.connector.cdc.mysql.source.debezium;

import io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.kafka.connect.json.JsonConverter;
import org.apache.kafka.connect.runtime.WorkerConfig;
import org.apache.kafka.connect.storage.MemoryOffsetBackingStore;

/**
 * Attempt-local Debezium offset cache seeded only from a completed YakFlow Source checkpoint.
 *
 * <p>Debezium may acknowledge prefetched records into this volatile store, but those
 * acknowledgments are never used for YakFlow checkpoints. Each new attempt registers its
 * mailbox-emitted offset; a failed engine's volatile internal progress is discarded.
 */
public final class CheckpointOffsetBackingStore extends MemoryOffsetBackingStore {

    private static final String SESSION_PROPERTY = "yakflow.cdc.session.id";
    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();

    private Session session;

    /** Registers an isolated engine attempt before Debezium instantiates its offset store. */
    public static String register(String name, BinlogOffset restored) {
        String id = UUID.randomUUID().toString();
        SESSIONS.put(id, new Session(Objects.requireNonNull(name, "name"), restored));
        return id;
    }

    public static void unregister(String sessionId) {
        SESSIONS.remove(sessionId);
    }

    @Override
    public void configure(WorkerConfig config) {
        super.configure(config);
        Object key = config.originals().get(SESSION_PROPERTY);
        if (!(key instanceof String id) || (session = SESSIONS.get(id)) == null) {
            throw new IllegalStateException("Debezium has no registered YakFlow Checkpoint offset session");
        }
        if (session.restored() != null) {
            JsonConverter converter = new JsonConverter();
            converter.configure(Map.of("schemas.enable", "false"), true);
            BinlogOffset offset = session.restored();
            byte[] part = converter.fromConnectData(session.name(), null, List.of(session.name(), offset.partition()));
            byte[] cursor = converter.fromConnectData(session.name(), null, offset.position());
            data.put(ByteBuffer.wrap(part), ByteBuffer.wrap(cursor));
        }
    }

    @Override
    public Set<Map<String, Object>> connectorPartitions(String connectorName) {
        if (session == null || session.restored() == null || !session.name().equals(connectorName)) {
            return Set.of();
        }
        return Set.of(new LinkedHashMap<>(session.restored().partition()));
    }

    private record Session(String name, BinlogOffset restored) {}
}
