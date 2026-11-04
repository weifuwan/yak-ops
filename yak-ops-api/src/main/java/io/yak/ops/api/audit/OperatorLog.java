package io.yak.ops.api.audit;

import io.yak.ops.api.audit.enums.AuditType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation for logging and auditing operator actions in the system.
 * This annotation can be applied to methods to indicate the type of operation, object type,
 * and specific parameters to be recorded in the logs.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperatorLog {

    AuditType auditType();
}
