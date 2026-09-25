package io.yak.ops.security.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记无需登录态即可访问的 Controller 或 Controller 方法。
 *
 * <p>该注解只跳过登录校验，不表达角色、权限或资源授权语义。</p>
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicEndpoint {}
