package io.yak.ops.security.authentication;

import io.yak.ops.common.util.ObjectUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.security.config.YakSecurityProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 按用户名和来源 IP 记录连续登录失败，并在达到阈值后执行临时锁定。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public final class LoginAttemptGuard {

    private final ConcurrentMap<String, FailureState> failures = new ConcurrentHashMap<>();
    private final int maximumFailures;
    private final Duration lockDuration;
    private final Clock clock;

    public LoginAttemptGuard(YakSecurityProperties.LoginSecurityProperties properties) {
        this(properties, Clock.systemUTC());
    }

    LoginAttemptGuard(YakSecurityProperties.LoginSecurityProperties properties, Clock clock) {
        if (ObjectUtils.isNull(properties)) {
            throw new IllegalArgumentException("login security properties must not be null");
        }
        if (ObjectUtils.isNull(clock)) {
            throw new IllegalArgumentException("clock must not be null");
        }
        if (properties.getMaxFailureCount() < 1) {
            throw new IllegalArgumentException("max-failure-count must be greater than 0");
        }
        if (ObjectUtils.isNull(properties.getLockDuration())
                || properties.getLockDuration().isNegative()
                || properties.getLockDuration().isZero()) {
            throw new IllegalArgumentException("lock-duration must be greater than 0");
        }
        this.maximumFailures = properties.getMaxFailureCount();
        this.lockDuration = properties.getLockDuration();
        this.clock = clock;
    }

    public boolean isBlocked(String username, String ipAddress) {
        Instant now = clock.instant();
        return isBlocked(key("user", normalizeUsername(username)), now)
                || isBlocked(key("ip", normalizeIp(ipAddress)), now);
    }

    public void recordFailure(String username, String ipAddress) {
        Instant now = clock.instant();
        recordFailure(key("user", normalizeUsername(username)), now);
        recordFailure(key("ip", normalizeIp(ipAddress)), now);
    }

    public void recordSuccess(String username, String ipAddress) {
        failures.remove(key("user", normalizeUsername(username)));
        failures.remove(key("ip", normalizeIp(ipAddress)));
    }

    private boolean isBlocked(String key, Instant now) {
        FailureState state = failures.get(key);
        if (ObjectUtils.isNull(state) || ObjectUtils.isNull(state.blockedUntil)) {
            return false;
        }
        if (!now.isBefore(state.blockedUntil)) {
            failures.remove(key, state);
            return false;
        }
        return true;
    }

    private void recordFailure(String key, Instant now) {
        failures.compute(key, (ignored, current) -> {
            if (ObjectUtils.isNull(current) || isLockExpired(current, now)) {
                current = new FailureState(0, null);
            }
            int count = current.count + 1;
            Instant blockedUntil = count >= maximumFailures ? now.plus(lockDuration) : null;
            return new FailureState(count, blockedUntil);
        });
    }

    private static boolean isLockExpired(FailureState state, Instant now) {
        return ObjectUtils.isNotNull(state.blockedUntil) && !now.isBefore(state.blockedUntil);
    }

    private static String key(String dimension, String value) {
        return dimension + ':' + value;
    }

    private static String normalizeUsername(String username) {
        return ObjectUtils.isNull(username) ? "<blank>" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeIp(String ipAddress) {
        return StringUtils.isBlank(ipAddress) ? "<unknown>" : ipAddress.trim();
    }

    /**
     * 单一用户名或 IP 维度的失败次数与锁定截止时间。
     *
     * @author weifuwan
     * @since 2026-09-24
     */
    private record FailureState(int count, Instant blockedUntil) {}
}
