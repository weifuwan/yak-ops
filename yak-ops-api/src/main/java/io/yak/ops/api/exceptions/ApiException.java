package io.yak.ops.api.exceptions;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.yak.ops.api.enums.Status;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * controller exception annotation
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface ApiException {

    Status value();
}
