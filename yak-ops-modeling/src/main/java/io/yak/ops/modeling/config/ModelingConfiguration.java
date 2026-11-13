package io.yak.ops.modeling.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring integration for the data modeling business module.
 *
 * <p>The module owns its services and persistence mappers while reusing Yak Ops'
 * shared datasource, MyBatis session factory, authentication and project services.
 */
@Configuration
@ComponentScan(basePackages = "io.yak.ops.modeling")
@MapperScan(
        basePackages = "io.yak.ops.modeling.infrastructure.persistence.mapper",
        sqlSessionFactoryRef = "sqlSessionFactory")
public class ModelingConfiguration {
}
