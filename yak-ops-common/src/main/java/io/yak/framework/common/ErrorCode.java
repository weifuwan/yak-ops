package io.yak.framework.common;

/**
 * 跨模块错误码契约。
 *
 * <p>各业务模块只需实现此接口并维护自己的错误码，不需要把业务枚举放入
 * {@code yak-common}。</p>
 */
public interface ErrorCode {

    /**
     * 返回稳定的错误码。
     */
    Integer getCode();

    /**
     * 返回可展示的错误信息。
     */
    String getMessage();
}
