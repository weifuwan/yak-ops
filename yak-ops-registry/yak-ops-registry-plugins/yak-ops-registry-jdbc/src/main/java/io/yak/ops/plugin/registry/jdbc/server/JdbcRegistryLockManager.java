package io.yak.ops.plugin.registry.jdbc.server;

import io.yak.ops.common.thread.ThreadUtils;
import io.yak.ops.plugin.registry.jdbc.JdbcRegistryProperties;
import io.yak.ops.plugin.registry.jdbc.LockUtils;
import io.yak.ops.plugin.registry.jdbc.model.DTO.JdbcRegistryLockDTO;
import io.yak.ops.plugin.registry.jdbc.repository.JdbcRegistryLockRepository;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.dao.DuplicateKeyException;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JdbcRegistryLockManager implements IJdbcRegistryLockManager {

    private final JdbcRegistryProperties jdbcRegistryProperties;
    private final JdbcRegistryLockRepository jdbcRegistryLockRepository;

    // lockKey -> LockEntry
    private final Map<String, LockEntry> jdbcRegistryLockHolderMap = new ConcurrentHashMap<>();

    public JdbcRegistryLockManager(JdbcRegistryProperties jdbcRegistryProperties,
                                   JdbcRegistryLockRepository jdbcRegistryLockRepository) {
        this.jdbcRegistryProperties = jdbcRegistryProperties;
        this.jdbcRegistryLockRepository = jdbcRegistryLockRepository;
    }

    @Override
    public void acquireJdbcRegistryLock(Long clientId, String lockKey) {
        String lockOwner = LockUtils.getLockOwner();
        while (true) {
            if (tryReenterLock(lockKey, lockOwner)) {
                return;
            }
            JdbcRegistryLockDTO jdbcRegistryLock = JdbcRegistryLockDTO.builder()
                    .lockKey(lockKey)
                    .clientId(clientId)
                    .lockOwner(lockOwner)
                    .createTime(new Date())
                    .build();
            try {
                jdbcRegistryLockRepository.insert(jdbcRegistryLock);
                if (jdbcRegistryLock != null) {
                    jdbcRegistryLockHolderMap.put(lockKey, LockEntry.builder()
                            .lockKey(lockKey)
                            .lockOwner(lockOwner)
                            .jdbcRegistryLock(jdbcRegistryLock)
                            .build());
                    return;
                }
                log.debug("{} acquire the lock {} success", lockOwner, lockKey);
            } catch (DuplicateKeyException duplicateKeyException) {
                // The lock is already exist, wait it release.
                continue;
            }
            log.debug("{} acquire the lock {} failed try again", lockOwner, lockKey);
            // acquire failed, wait and try again
            ThreadUtils.sleep(jdbcRegistryProperties.getHeartbeatRefreshInterval().toMillis());
        }
    }

    private boolean tryReenterLock(String lockKey, String lockAcquirer) {
        LockEntry lockEntry = jdbcRegistryLockHolderMap.get(lockKey);
        if (lockEntry != null && lockAcquirer.equals(lockEntry.getLockOwner())) {
            lockEntry.lockCount.incrementAndGet();
            return true;
        }
        return false;
    }

    @Override
    public boolean acquireJdbcRegistryLock(Long clientId, String lockKey, long timeout) {
        String lockOwner = LockUtils.getLockOwner();
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start <= timeout) {
            if (tryReenterLock(lockKey, lockOwner)) {
                return true;
            }
            JdbcRegistryLockDTO jdbcRegistryLock = JdbcRegistryLockDTO.builder()
                    .lockKey(lockKey)
                    .clientId(clientId)
                    .lockOwner(lockOwner)
                    .createTime(new Date())
                    .build();
            try {
                jdbcRegistryLockRepository.insert(jdbcRegistryLock);
                if (jdbcRegistryLock != null) {
                    jdbcRegistryLockHolderMap.put(lockKey, LockEntry.builder()
                            .lockKey(lockKey)
                            .lockOwner(lockOwner)
                            .jdbcRegistryLock(jdbcRegistryLock)
                            .build());
                    return true;
                }
                log.debug("{} acquire the lock {} success", lockOwner, lockKey);
            } catch (DuplicateKeyException duplicateKeyException) {
                // The lock is already exist, wait it release.
                continue;
            }
            log.debug("{} acquire the lock {} failed try again", lockOwner, lockKey);
            // acquire failed, wait and try again
            ThreadUtils.sleep(jdbcRegistryProperties.getHeartbeatRefreshInterval().toMillis());
        }
        return false;
    }

    @Override
    public void releaseJdbcRegistryLock(Long clientId, String lockKey) {
        String lockOwner = LockUtils.getLockOwner();
        LockEntry lockEntry = jdbcRegistryLockHolderMap.get(lockKey);
        if (lockEntry == null || !lockOwner.equals(lockEntry.getLockOwner())) {
            return;
        }
        if (!clientId.equals(lockEntry.getJdbcRegistryLock().getClientId())) {
            throw new UnsupportedOperationException(
                    "The client " + clientId + " is not the lock owner of the lock: " + lockKey);
        }
        int newLockCount = lockEntry.lockCount.decrementAndGet();
        if (newLockCount > 0) {
            return;
        }
        if (newLockCount < 0) {
            throw new IllegalMonitorStateException("Jdbc lock count has gone negative for lock: " + lockKey);
        }
        jdbcRegistryLockRepository.deleteById(lockEntry.getJdbcRegistryLock().getId());
        jdbcRegistryLockHolderMap.remove(lockKey);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LockEntry {

        private String lockKey;
        private String lockOwner;
        final AtomicInteger lockCount = new AtomicInteger(1);
        private JdbcRegistryLockDTO jdbcRegistryLock;
    }
}
