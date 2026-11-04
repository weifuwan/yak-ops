package io.yak.ops;

import io.yak.ops.tools.datasource.InitializeYakOps;
import io.yak.ops.common.lifecycle.ServerLifeCycleManager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootApplication
public class StandaloneServer {

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
