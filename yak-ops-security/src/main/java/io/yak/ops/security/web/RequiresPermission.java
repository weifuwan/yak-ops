package io.yak.ops.security.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an MVC endpoint as requiring an RBAC permission.
 *
 * <p>The value is the permission code stored in
 * {@code yak_security_permission.permission_code}. The annotation may be put
 * on a controller class or on an individual handler method; a method-level
 * annotation overrides a class-level annotation.</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface RequiresPermission {

    /**
     * Required permission code.
     *
     * @return permission code
     */
    String value();
}
