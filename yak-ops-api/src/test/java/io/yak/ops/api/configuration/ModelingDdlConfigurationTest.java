package io.yak.ops.api.configuration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.api.ApiApplicationServer;
import io.yak.ops.dao.DaoConfiguration;
import io.yak.ops.modeling.ddl.api.DdlSessionFactory;
import io.yak.ops.modeling.infrastructure.ddl.PluginDataSourceSessionFactory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/** Verifies that the API runtime registers the plugin-backed DDL session factory. */
@SpringBootTest(classes = {ApiApplicationServer.class, DaoConfiguration.class})
class ModelingDdlConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldRegisterPluginBackedDdlSessionFactory() {
        assertTrue(applicationContext.getBean(DdlSessionFactory.class) instanceof PluginDataSourceSessionFactory);
    }
}
