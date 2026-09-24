package io.yak.framework.security.web;

import java.lang.annotation.*;

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
