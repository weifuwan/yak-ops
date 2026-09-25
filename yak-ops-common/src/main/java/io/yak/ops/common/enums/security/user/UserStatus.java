package io.yak.ops.common.enums.security.user;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 用户账号状态。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
@Getter
@RequiredArgsConstructor
public enum UserStatus {
    ACTIVE(1, "正常"),
    DISABLED(2, "禁用");

    @EnumValue
    private final Integer value;

    private final String displayName;
}
