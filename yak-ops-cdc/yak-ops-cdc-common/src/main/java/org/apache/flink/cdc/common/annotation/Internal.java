package org.apache.flink.cdc.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Annotation to mark class and methods as an internal developer API that should not be used by
 * users. Internal APIs are not stable and might change across releases.
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
@Internal
public @interface Internal {}
