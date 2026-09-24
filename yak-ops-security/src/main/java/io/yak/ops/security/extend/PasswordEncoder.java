package io.yak.ops.security.extend;

/**
 * 密码编码器扩展点。
 *
 * <p>密码编码必须使用不可逆的安全散列算法，不应保存或返回明文密码。</p>
 *
 * @author weifuwan
 */
public interface PasswordEncoder {

    /**
     * 对原始密码进行不可逆编码。
     *
     * @param rawPassword 原始密码
     * @return 编码后的密码
     */
    String encode(CharSequence rawPassword);

    /**
     * 判断原始密码与编码后的密码是否匹配。
     *
     * @param rawPassword 原始密码
     * @param encodedPassword 编码后的密码
     * @return 匹配返回 {@code true}
     */
    boolean matches(CharSequence rawPassword, String encodedPassword);
}
