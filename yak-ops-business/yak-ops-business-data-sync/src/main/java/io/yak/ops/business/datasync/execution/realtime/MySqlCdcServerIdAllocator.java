package io.yak.ops.business.datasync.execution.realtime;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;
import org.springframework.stereotype.Component;

/**
 * 为当前单节点进程中的 MySQL CDC 执行分配互不冲突的 replication serverId。
 *
 * <p>首选 ID 由稳定 realtime state key 派生，因此同一任务版本在无冲突时会获得相同 ID；
 * 若与其他活动任务冲突，则顺序探测下一个可用 ID。执行结束后释放占用。</p>
 *
 * @author weifuwan
 * @since 2026-09-28
 */
@Component
public class MySqlCdcServerIdAllocator {

    private static final long MIN_SERVER_ID = 1L;
    private static final long MAX_SERVER_ID = 4_294_967_295L;

    private final Map<String, Long> allocationByStateKey = new HashMap<>();
    private final Map<Long, String> ownerByServerId = new HashMap<>();

    public synchronized long allocate(String stateKey) {
        if (stateKey == null || stateKey.isBlank()) {
            throw new IllegalArgumentException("realtime state key must not be blank");
        }

        Long existing = allocationByStateKey.get(stateKey);
        if (existing != null) return existing;

        long candidate = preferredServerId(stateKey);
        long firstCandidate = candidate;
        do {
            String owner = ownerByServerId.get(candidate);
            if (owner == null || owner.equals(stateKey)) {
                ownerByServerId.put(candidate, stateKey);
                allocationByStateKey.put(stateKey, candidate);
                return candidate;
            }
            candidate = candidate == MAX_SERVER_ID ? MIN_SERVER_ID : candidate + 1;
        } while (candidate != firstCandidate);

        throw new IllegalStateException("no available MySQL CDC serverId");
    }

    public synchronized void release(String stateKey, long serverId) {
        Long allocated = allocationByStateKey.get(stateKey);
        if (allocated == null || allocated != serverId) return;
        allocationByStateKey.remove(stateKey);
        ownerByServerId.remove(serverId, stateKey);
    }

    static long preferredServerId(String stateKey) {
        CRC32 checksum = new CRC32();
        checksum.update(stateKey.getBytes(StandardCharsets.UTF_8));
        long value = checksum.getValue();
        return value < MIN_SERVER_ID ? MIN_SERVER_ID : value;
    }
}
