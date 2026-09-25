package io.yak.ops.common.util;

import io.yak.ops.common.exception.BusinessException;
import java.net.InetAddress;

/**
 * 雪花算法 ID 工具。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
public final class IdUtils {

    private static final long EPOCH = 1767225600000L;
    private static final long WORKER_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);
    private static final long WORKER_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_BITS;
    private static final long WORKER_ID = resolveWorkerId();

    private static long sequence;
    private static long lastTimestamp = -1L;

    private IdUtils() {}

    /** 生成全局唯一雪花 ID。 */
    public static synchronized String nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new BusinessException("Clock moved backwards, unable to generate snowflake ID");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = nextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        long id = ((timestamp - EPOCH) << TIMESTAMP_SHIFT) | (WORKER_ID << WORKER_SHIFT) | sequence;
        return Long.toString(id);
    }

    private static long nextMillis(long timestamp) {
        long current = System.currentTimeMillis();
        while (current <= timestamp) {
            current = System.currentTimeMillis();
        }
        return current;
    }

    private static long resolveWorkerId() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            long pid = ProcessHandle.current().pid();
            return Math.floorMod((host + "-" + pid).hashCode(), MAX_WORKER_ID + 1);
        } catch (Exception ignored) {
            return Math.floorMod(ProcessHandle.current().pid(), MAX_WORKER_ID + 1);
        }
    }
}
