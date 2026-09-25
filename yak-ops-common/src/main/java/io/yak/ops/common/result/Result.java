package io.yak.ops.common.result;

import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.exception.BusinessException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 统一接口返回结果。
 *
 * <p>封装接口执行状态、提示信息和业务数据。</p>
 *
 * @param <T> 返回数据类型
 * @author weifuwan
 * @since 2026-09-25
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode
public class Result<T> {

    /** 接口执行结果提示信息。 */
    private String message;

    /** 接口执行结果状态码。 */
    private Integer code;

    /** 返回的业务数据。 */
    private T data;

    private Result(Integer code) {
        this.code = code;
    }

    private Result(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public static <T> Result<T> build(boolean success) {
        return success ? Result.success() : Result.fail();
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = success();
        result.setData(data);
        return result;
    }

    public static <T> Result<T> success() {
        return new Result<>(CommonErrorCode.SUCCESS.getCode(), CommonErrorCode.SUCCESS.getMessage());
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        if (errorCode == null) return fail();
        return new Result<>(errorCode.getCode(), errorCode.getMessage());
    }

    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(CommonErrorCode.COMMON_FAIL.getCode(), message);
    }

    public static <T> Result<T> fail() {
        return fail(CommonErrorCode.COMMON_FAIL);
    }

    public static <T> Result<T> fail(BusinessException exception) {
        if (exception == null) return fail();
        if (exception.getErrorCode() != null) return fail(exception.getErrorCode());
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty() ? fail() : fail(message);
    }

    public static <T> Result<T> buildFrom(Result<?> source) {
        if (source == null) return fail();
        return new Result<>(source.getCode(), source.getMessage());
    }

    public static <T> Result<T> buildParamIllegal(String message) {
        String detail = message == null ? "" : message.trim();
        return new Result<>(
                CommonErrorCode.PARAM_NOT_VALID.getCode(),
                CommonErrorCode.PARAM_NOT_VALID.getMessage() + (detail.isEmpty() ? "" : "：" + detail) + "，请检查后再提交！");
    }

    public static <T> Result<T> buildNotExist(String message) {
        return new Result<>(CommonErrorCode.RESOURCE_NOT_EXISTS.getCode(), message);
    }

    public static <T> Result<T> buildDuplicate(String message) {
        return new Result<>(CommonErrorCode.RESOURCE_DUPLICATION.getCode(), message);
    }

    public boolean succeeded() {
        return CommonErrorCode.SUCCESS.getCode().equals(getCode());
    }

    public boolean duplicate() {
        return CommonErrorCode.RESOURCE_DUPLICATION.getCode().equals(getCode());
    }

    public boolean failed() {
        return !succeeded();
    }
}
