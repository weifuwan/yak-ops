package io.yak.ops.security.extend.impl;

import io.yak.ops.security.config.YakSecurityProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 本地登录失败次数限制器。
 *
 * <p>分别按照标准化后的用户名和来源 IP 统计登录失败次数，
 * 避免仅轮换用户名或 IP 地址绕过登录保护。</p>
 *
 * <p>当前实现仅适用于单节点应用。集群环境中应使用 Redis
 * 或其他支持原子操作的共享存储实现。</p>
 *
 * @author weifuwan
 */
public final class LoginAttemptGuard {

    /**
     * 登录失败状态缓存。
     */
    private final ConcurrentMap<String, FailureState> failures = new ConcurrentHashMap<>();

    /**
     * 触发锁定的最大失败次数。
     */
    private final int maximumFailures;

    /**
     * 登录锁定时长。
     */
    private final Duration lockDuration;

    /**
     * 系统时钟。
     */
    private final Clock clock;

    /**
     * 使用系统 UTC 时钟创建登录失败限制器。
     *
     * @param properties 登录安全配置
     */
    public LoginAttemptGuard(YakSecurityProperties.LoginSecurityProperties properties) {
        this(properties, Clock.systemUTC());
    }

    /**
     * 使用指定时钟创建登录失败限制器。
     *
     * <p>该构造方法主要用于单元测试。</p>
     *
     * @param properties 登录安全配置
     * @param clock      系统时钟
     */
    LoginAttemptGuard(YakSecurityProperties.LoginSecurityProperties properties, Clock clock) {

        if (properties == null) {
            throw new IllegalArgumentException("login security properties must not be null");
        }

        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }

        if (properties.getMaxFailureCount() < 1) {
            throw new IllegalArgumentException("max-failure-count must be greater than 0");
        }

        if (properties.getLockDuration() == null
                || properties.getLockDuration().isNegative()
                || properties.getLockDuration().isZero()) {
            throw new IllegalArgumentException("lock-duration must be greater than 0");
        }

        this.maximumFailures = properties.getMaxFailureCount();
        this.lockDuration = properties.getLockDuration();
        this.clock = clock;
    }

    /**
     * 判断指定用户或来源 IP 是否已被锁定。
     *
     * @param username  用户名
     * @param ipAddress 来源 IP
     * @return 任一维度被锁定时返回 {@code true}
     */
    public boolean isBlocked(String username, String ipAddress) {
        Instant now = clock.instant();

        return isBlocked(key("user", normalizeUsername(username)), now)
                || isBlocked(key("ip", normalizeIp(ipAddress)), now);
    }

    /**
     * 记录一次登录失败。
     *
     * <p>用户名和来源 IP 将分别累计失败次数。</p>
     *
     * @param username  用户名
     * @param ipAddress 来源 IP
     */
    public void recordFailure(String username, String ipAddress) {
        Instant now = clock.instant();

        recordFailure(key("user", normalizeUsername(username)), now);

        recordFailure(key("ip", normalizeIp(ipAddress)), now);
    }

    /**
     * 记录登录成功并清除相关失败状态。
     *
     * @param username  用户名
     * @param ipAddress 来源 IP
     */
    public void recordSuccess(String username, String ipAddress) {
        failures.remove(key("user", normalizeUsername(username)));

        failures.remove(key("ip", normalizeIp(ipAddress)));
    }

    /**
     * 判断指定缓存键是否处于锁定状态。
     *
     * @param key 缓存键
     * @param now 当前时间
     * @return 处于锁定状态时返回 {@code true}
     */
    private boolean isBlocked(String key, Instant now) {
        FailureState state = failures.get(key);

        if (state == null || state.blockedUntil == null) {
            return false;
        }

        if (!now.isBefore(state.blockedUntil)) {
            failures.remove(key, state);
            return false;
        }

        return true;
    }

    /**
     * 为指定缓存键记录一次失败。
     *
     * @param key 缓存键
     * @param now 当前时间
     */
    private void recordFailure(String key, final Instant now) {
        failures.compute(key, (ignored, current) -> {
            if (current == null || isLockExpired(current, now)) {
                current = new FailureState(0, null);
            }

            int count = current.count + 1;

            Instant blockedUntil = count >= maximumFailures ? now.plus(lockDuration) : null;

            return new FailureState(count, blockedUntil);
        });
    }

    /**
     * 判断锁定状态是否已经过期。
     *
     * @param state 失败状态
     * @param now   当前时间
     * @return 锁定已过期时返回 {@code true}
     */
    private static boolean isLockExpired(FailureState state, Instant now) {

        return state.blockedUntil != null && !now.isBefore(state.blockedUntil);
    }

    /**
     * 构建缓存键。
     *
     * @param dimension 统计维度
     * @param value     维度值
     * @return 缓存键
     */
    private static String key(String dimension, String value) {
        return dimension + ':' + value;
    }

    /**
     * 标准化用户名。
     *
     * @param username 用户名
     * @return 标准化后的用户名
     */
    private static String normalizeUsername(String username) {
        return username == null ? "<blank>" : username.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 标准化来源 IP。
     *
     * @param ipAddress 来源 IP
     * @return 标准化后的来源 IP
     */
    private static String normalizeIp(String ipAddress) {
        return ipAddress == null || ipAddress.trim().isEmpty() ? "<unknown>" : ipAddress.trim();
    }

    /**
     * 登录失败状态。
     */
    private static final class FailureState {

        /**
         * 当前累计失败次数。
         */
        private final int count;

        /**
         * 锁定截止时间。
         *
         * <p>未达到锁定条件时为 {@code null}。</p>
         */
        private final Instant blockedUntil;

        /**
         * 创建登录失败状态。
         *
         * @param count        累计失败次数
         * @param blockedUntil 锁定截止时间
         */
        private FailureState(int count, Instant blockedUntil) {
            this.count = count;
            this.blockedUntil = blockedUntil;
        }
    }
}
