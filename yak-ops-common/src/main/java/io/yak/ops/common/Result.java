package io.yak.ops.common;

import io.yak.ops.common.exception.BusinessException;

import lombok.*;

/**
 * 统一接口返回结果。
 *
 * <p>封装接口执行状态、提示信息和业务数据。</p>
 *
 * @param <T> 返回数据类型
 * @author weifuwan
 */
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Result<T> extends BaseResult {

    /**
     * 返回的业务数据。
     */
    protected T data;

    /**
     * 创建只包含状态码的返回结果。
     *
     * @param code 状态码
     */
    private Result(Integer code) {
        this.code = code;
    }

    /**
     * 创建包含状态码和提示信息的返回结果。
     *
     * @param code    状态码
     * @param message 提示信息
     */
    private Result(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 根据布尔值构建返回结果。
     *
     * @param success 是否成功
     * @param <T>     返回数据类型
     * @return 返回结果
     */
    public static <T> Result<T> build(boolean success) {
        return success ? Result.success() : Result.fail();
    }

    /**
     * 构建成功结果，并携带业务数据。
     *
     * @param data 业务数据
     * @param <T>  返回数据类型
     * @return 成功结果
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = success();
        result.setData(data);
        return result;
    }

    /**
     * 构建不携带业务数据的成功结果。
     *
     * @param <T> 返回数据类型
     * @return 成功结果
     */
    public static <T> Result<T> success() {
        return new Result<>(
                CommonErrorCode.SUCCESS.getCode(),
                CommonErrorCode.SUCCESS.getMessage()
        );
    }

    /**
     * 根据结果码构建失败结果。
     *
     * @param errorCode 错误码定义
     * @param <T>       返回数据类型
     * @return 失败结果
     */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        if (errorCode == null) {
            return fail();
        }

        return new Result<>(
                errorCode.getCode(),
                errorCode.getMessage()
        );
    }

    /**
     * 根据状态码和提示信息构建失败结果。
     *
     * @param code    状态码
     * @param message 提示信息
     * @param <T>     返回数据类型
     * @return 失败结果
     */
    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message);
    }

    /**
     * 根据提示信息构建通用失败结果。
     *
     * @param message 提示信息
     * @param <T>     返回数据类型
     * @return 失败结果
     */
    public static <T> Result<T> fail(String message) {
        return new Result<>(
                CommonErrorCode.COMMON_FAIL.getCode(),
                message
        );
    }

    /**
     * 构建通用失败结果。
     *
     * @param <T> 返回数据类型
     * @return 失败结果
     */
    public static <T> Result<T> fail() {
        return fail(CommonErrorCode.COMMON_FAIL);
    }

    /**
     * 根据业务异常构建失败结果。
     *
     * <p>优先使用异常携带的结构化错误码；只有未提供错误码时才使用异常消息。</p>
     *
     * @param exception 业务异常
     * @param <T>       返回数据类型
     * @return 失败结果
     */
    public static <T> Result<T> fail(BusinessException exception) {
        if (exception == null) {
            return fail();
        }
        if (exception.getErrorCode() != null) {
            return fail(exception.getErrorCode());
        }
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? fail()
                : fail(message);
    }

    /**
     * 根据已有结果复制状态码和提示信息。
     *
     * <p>该方法只复制状态信息，不复制原结果中的业务数据。</p>
     *
     * @param source 原始返回结果
     * @param <T>    新返回结果的数据类型
     * @return 新返回结果
     */
    public static <T> Result<T> buildFrom(Result<?> source) {
        if (source == null) {
            return fail();
        }

        return new Result<>(
                source.getCode(),
                source.getMessage()
        );
    }

    /**
     * 构建参数校验失败结果。
     *
     * @param message 参数校验说明
     * @param <T>     返回数据类型
     * @return 参数校验失败结果
     */
    public static <T> Result<T> buildParamIllegal(String message) {
        String detail = message == null ? "" : message.trim();

        return new Result<>(
                CommonErrorCode.PARAM_NOT_VALID.getCode(),
                CommonErrorCode.PARAM_NOT_VALID.getMessage()
                        + (detail.isEmpty() ? "" : "：" + detail)
                        + "，请检查后再提交！"
        );
    }

    /**
     * 构建资源不存在结果。
     *
     * @param message 提示信息
     * @param <T>     返回数据类型
     * @return 资源不存在结果
     */
    public static <T> Result<T> buildNotExist(String message) {
        return new Result<>(
                CommonErrorCode.RESOURCE_NOT_EXISTS.getCode(),
                message
        );
    }

    /**
     * 构建资源重复结果。
     *
     * @param message 提示信息
     * @param <T>     返回数据类型
     * @return 资源重复结果
     */
    public static <T> Result<T> buildDuplicate(String message) {
        return new Result<>(
                CommonErrorCode.RESOURCE_DUPLICATION.getCode(),
                message
        );
    }

    /**
     * 判断请求是否成功。
     *
     * @return 成功返回 {@code true}
     */
    public boolean succeeded() {
        return CommonErrorCode.SUCCESS.getCode().equals(getCode());
    }

    /**
     * 判断资源是否重复。
     *
     * @return 资源重复返回 {@code true}
     */
    public boolean duplicate() {
        return CommonErrorCode.RESOURCE_DUPLICATION.getCode().equals(getCode());
    }

    /**
     * 判断请求是否失败。
     *
     * @return 失败返回 {@code true}
     */
    public boolean failed() {
        return !succeeded();
    }
}
