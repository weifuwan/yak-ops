package io.yak.ops.security.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 用户唯一性校验字段类型。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
@Getter
@RequiredArgsConstructor
public enum UserCheckType {
    USER_NAME(1, "用户名"),
    USER_PHONE(2, "用户电话"),
    USER_MAIL(3, "用户邮箱");

    private final int code;
    private final String displayName;
}
