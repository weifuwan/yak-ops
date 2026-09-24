package io.yak.ops.core.project;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a controller or endpoint as participating in the Project Space rollout. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ProjectScope {

  ProjectMigrationMode value() default ProjectMigrationMode.PROJECT_REQUIRED;
}
