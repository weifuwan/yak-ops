package io.yak.ops.common;

import lombok.NoArgsConstructor;

/**
 * 接口返回结果基础类。
 *
 * <p>统一封装接口执行状态码和提示信息，具体业务返回对象可继承该类。</p>
 *
 * @author weifuwan
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode
public class BaseResult {

    /**
     * 接口执行结果提示信息。
     */
    protected String message;

    /**
     * 接口执行结果状态码。
     */
    protected Integer code;
}
