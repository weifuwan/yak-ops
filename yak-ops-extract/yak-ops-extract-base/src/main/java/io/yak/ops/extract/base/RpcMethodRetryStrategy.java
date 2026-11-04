package io.yak.ops.extract.base;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.ConnectException;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RpcMethodRetryStrategy {

    /**
     * The maximum number of retries. Default is 3, which means that the method is retried at most 3 times, including the first call.
     */
    int maxRetryTimes() default 3;

    /**
     * The interval between retries, in milliseconds. If the value is less than or equal to 0, no interval is set.
     */
    long retryInterval() default 0;

    /**
     * Which exception to retry.
     * <p> Default is {@link ConnectException}.
     */
    Class<? extends Throwable>[] retryFor() default {ConnectException.class};

}
