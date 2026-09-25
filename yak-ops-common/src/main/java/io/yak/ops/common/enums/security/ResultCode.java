package io.yak.ops.common.enums.security;

import io.yak.ops.common.result.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 用户与登录能力共享的业务结果码。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
@Getter
@RequiredArgsConstructor
public enum ResultCode implements ErrorCode {
    SUCCESS(200, "成功"),
    COMMON_FAIL(999, "失败"),
    PARAM_NOT_VALID(1001, "参数无效"),
    PARAM_IS_BLANK(1002, "参数为空"),
    PARAM_ID_IS_BLANK(1003, "参数id为空"),
    PARAM_TYPE_ERROR(1004, "参数类型错误"),
    PARAM_NOT_COMPLETE(1005, "参数缺失"),
    PARAM_LENGTH_ERROR(1006, "参数长度不正确"),
    PARAM_ERROR(1007, "参数错误"),
    USER_NOT_LOGIN(2001, "用户未登录"),
    USER_ACCOUNT_EXPIRED(2002, "账号已过期"),
    USER_CREDENTIALS_ERROR(2003, "密码错误"),
    USER_CREDENTIALS_EXPIRED(2004, "密码过期"),
    USER_ACCOUNT_DISABLE(2005, "账号不可用"),
    USER_ACCOUNT_LOCKED(2006, "账号被锁定"),
    USER_ACCOUNT_NOT_EXIST(2007, "账号不存在"),
    USER_ACCOUNT_ALREADY_EXIST(2008, "账号已存在"),
    USER_ACCOUNT_USE_BY_OTHERS(2009, "账号下线"),
    USER_ACCOUNT_INSERT_FAIL(2010, "用户注册失败"),
    USER_PHONE_EXIST(2011, "手机号已存在"),
    USER_EMAIL_FORMAT_ERROR(2012, "邮箱格式错误"),
    USER_EMAIL_EXIST(2013, "邮箱已存在"),
    USER_PASSWORD_DECRYPT_ERROR(2014, "密码解密出错"),
    USER_PASSWORD_ENCODE_ERROR(2015, "密码编码出错"),
    USER_ID_CANNOT_BE_NULL(2016, "用户id不可为空"),
    USER_NOT_EXISTS(2017, "用户不存在"),
    USER_ACCOUNT_UPDATE_FAIL(2018, "用户更新失败"),
    USER_PHONE_FORMAT_ERROR(2019, "手机号格式错误"),
    USER_NAME_FORMAT_ERROR(2020, "用户名格式错误"),
    USER_NAME_EXISTS(2021, "用户名已经存在"),
    NO_PERMISSION(3001, "没有权限");

    private final Integer code;
    private final String message;

    public static String getMessageByCode(Integer code) {
        for (ResultCode value : values()) {
            if (value.getCode().equals(code)) return value.getMessage();
        }
        return null;
    }
}
