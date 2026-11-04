package io.yak.ops;

import io.yak.ops.tools.datasource.InitializeYakOps;
import io.yak.ops.common.lifecycle.ServerLifeCycleManager;
import io.yak.ops.registry.api.RegistryClient;
import io.yak.ops.server.master.config.MasterConfig;
import io.yak.ops.server.worker.config.WorkerConfig;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootApplication
public class StandaloneServer {

    @Autowired
    private RegistryClient registryClient;

    @Autowired
    private MasterConfig masterConfig;

    @Autowired
    private WorkerConfig workerConfig;

    // ApplicationReadyEvent is published after Master, Worker and Alert have
    // received ApplicationStartedEvent; reject readiness if a registration is lost.
    @Order(200)
    @EventListener(ApplicationReadyEvent.class)
    public void verifyReady() {
        if (!registryClient.isConnected()) {
            throw new IllegalStateException("Standalone registry is not connected");
        }
        if (!registryClient.exists(masterConfig.getMasterRegistryPath())) {
            throw new IllegalStateException("Standalone master is missing from Registry");
        }
        if (!registryClient.exists(workerConfig.getWorkerRegistryPath())) {
            throw new IllegalStateException("Standalone worker is missing from Registry");
        }
        log.info("Standalone ready: Registry, Master and Worker registrations verified");
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        ServerLifeCycleManager.toStopped();
    }

    public static void main(String[] args) {
        // Run the existing, guarded schema initializer before any API, scheduler or registry
        // bean can access the database. A separate non-web context avoids a startup cycle.
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(InitializeYakOps.class)
                .web(WebApplicationType.NONE)
                .profiles("init")
                .run(args)) {
            log.info("Database schema initialized or validated");
        } catch (Exception ex) {
            log.error("StandaloneServer database initialization failed", ex);
            System.exit(1);
            return;
        }

        try {
            SpringApplication.run(StandaloneServer.class, args);
        } catch (Exception ex) {
            log.error("StandaloneServer start failed", ex);
            System.exit(1);
        }
    }
}
