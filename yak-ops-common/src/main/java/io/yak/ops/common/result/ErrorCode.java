package io.yak.ops.common.result;

/**
 * 跨模块错误码契约。
 *
 * <p>各业务能力只维护自己的错误码枚举，通过该接口进入统一 Result / BusinessException 边界。</p>
 *
 * @author weifuwan
 * @since 2026-09-25
 */
public interface ErrorCode {

    /** 返回稳定的错误码。 */
    Integer getCode();

    /** 返回可展示的错误信息。 */
    String getMessage();
}
