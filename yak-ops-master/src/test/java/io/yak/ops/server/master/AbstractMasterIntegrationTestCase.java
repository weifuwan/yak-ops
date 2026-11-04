package io.yak.ops.server.master;

import io.yak.ops.dao.DaoConfiguration;
import io.yak.ops.registry.api.RegistryClient;
import io.yak.ops.server.master.config.MasterConfig;
import io.yak.ops.server.master.integration.MasterContainer;
import io.yak.ops.server.master.integration.Repository;
import io.yak.ops.server.master.integration.WorkflowOperator;
import io.yak.ops.server.master.integration.WorkflowTestCaseContextFactory;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;

/**
 * The abstract class for master integration test.
 * <p> Used to create a text environment to test master server.
 * <p> In order to separate the environment for each text case, the context will be dirtied before each test method.
 */
@Slf4j
@SpringBootTest(classes = {
        MasterServer.class,
        DaoConfiguration.class}, properties = "spring.config.additional-location=classpath:/spring-it-application.yaml")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public abstract class AbstractMasterIntegrationTestCase {

    @Autowired
    protected WorkflowTestCaseContextFactory workflowTestCaseContextFactory;

    @Autowired
    protected WorkflowOperator workflowOperator;

    @Autowired
    protected Repository repository;

    @Autowired
    protected MasterContainer masterContainer;

    @Autowired
    protected RegistryClient registryClient;

    @Autowired
    protected MasterConfig masterConfig;

    @Autowired
    private MeterRegistry meterRegistry;

    /**
     * Unbind this test method's {@link MeterRegistry} from {@link Metrics#globalRegistry}.
     * <p>
     * Spring's metrics auto-configuration adds the per-context registry to {@code globalRegistry}
     * but does not remove it on teardown, so {@code @DirtiesContext} would otherwise leave dangling
     * child registries that pollute counter reads in later test methods.
     */
    @AfterEach
    public void unbindMeterRegistryFromGlobal() {
        Metrics.globalRegistry.remove(meterRegistry);
    }
}
