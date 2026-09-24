package io.yak.framework.security.permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares a permission that Yak Security registers during application startup. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface YakPermission {
  String code();
  String name();
  String group();

  /** Stable group code. When empty, the part of {@link #code()} before ':' is used. */
  String groupCode() default "";

  /** Stable menu code containing this action permission. */
  String menuCode() default "";

  String description() default "";
}
