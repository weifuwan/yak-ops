package io.yak.ops.security.extend.impl;

import io.yak.ops.security.extend.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.StringUtils;

/**
 * BCrypt 密码编码器默认实现。
 *
 * <p>使用 BCrypt 对新建和修改后的密码进行不可逆编码。</p>
 *
 * @author weifuwan
 */
public class DefaultPasswordEncoder implements PasswordEncoder {

    /**
     * BCrypt 默认强度。
     */
    private static final int DEFAULT_STRENGTH = 10;

    /**
     * Spring Security BCrypt 实现。
     */
    private final BCryptPasswordEncoder delegate;

    /**
     * 创建默认 BCrypt 密码编码器。
     */
    public DefaultPasswordEncoder() {
        this(DEFAULT_STRENGTH);
    }

    /**
     * 创建指定强度的 BCrypt 密码编码器。
     *
     * @param strength BCrypt 强度，范围通常为 4 到 31
     */
    public DefaultPasswordEncoder(int strength) {
        this.delegate = new BCryptPasswordEncoder(strength);
    }

    /**
     * 编码密码。
     *
     * @param rawPassword 原始密码
     * @return BCrypt 密码
     */
    @Override
    public String encode(CharSequence rawPassword) {

        if (rawPassword == null || !StringUtils.hasText(rawPassword.toString())) {

            throw new IllegalArgumentException("rawPassword must not be blank");
        }

        return delegate.encode(rawPassword);
    }

    /**
     * 判断密码是否匹配。
     *
     * @param rawPassword 原始密码
     * @param encodedPassword 编码后的密码
     * @return 匹配返回 {@code true}
     */
    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {

        if (rawPassword == null || !StringUtils.hasText(encodedPassword)) {
            return false;
        }

        try {
            return delegate.matches(rawPassword, encodedPassword);
        } catch (IllegalArgumentException exception) {
            // 数据库中的密码格式不合法时按不匹配处理，
            // 避免登录接口直接抛出底层编码异常。
            return false;
        }
    }
}
