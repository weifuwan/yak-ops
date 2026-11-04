package io.yak.ops.alert;

import io.yak.ops.alert.metrics.AlertServerMetrics;
import io.yak.ops.alert.plugin.AlertPluginManager;
import io.yak.ops.alert.registry.AlertRegistryClient;
import io.yak.ops.alert.rpc.AlertRpcServer;
import io.yak.ops.alert.service.AlertBootstrapService;
import io.yak.ops.alert.service.AlertHAServer;
import io.yak.ops.common.CommonConfiguration;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.lifecycle.ServerLifeCycleManager;
import io.yak.ops.common.thread.DefaultUncaughtExceptionHandler;
import io.yak.ops.common.thread.ThreadUtils;
import io.yak.ops.dao.DaoConfiguration;
import io.yak.ops.registry.api.RegistryConfiguration;
import io.yak.ops.registry.api.ha.AbstractServerStatusChangeListener;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Import({CommonConfiguration.class,
        DaoConfiguration.class,
        RegistryConfiguration.class})
@SpringBootApplication
public class AlertServer {

    private final AtomicBoolean stopped = new AtomicBoolean();

    @Value("${yak-ops.standalone:false}")
    private boolean standalone;

    @Autowired
    private AlertRpcServer alertRpcServer;

    @Autowired
    private AlertPluginManager alertPluginManager;

    @Autowired
    private AlertRegistryClient alertRegistryClient;

    @Autowired
    private AlertHAServer alertHAServer;

    @Autowired
    private AlertBootstrapService alertBootstrapService;

    public static void main(String[] args) {
        AlertServerMetrics.registerUncachedException(DefaultUncaughtExceptionHandler::getUncaughtExceptionCount);
        Thread.setDefaultUncaughtExceptionHandler(DefaultUncaughtExceptionHandler.getInstance());
        Thread.currentThread().setName(Constants.THREAD_NAME_ALERT_SERVER);
        SpringApplication.run(AlertServer.class, args);
    }

    @Order(30)
    @EventListener(ApplicationStartedEvent.class)
    public void run() {
        ServerLifeCycleManager.toRunning();
        log.info("AlertServer is staring ...");
        alertPluginManager.start();
        alertRpcServer.start();
        alertRegistryClient.start();

        alertHAServer.addServerStatusChangeListener(new AbstractServerStatusChangeListener() {

            @Override
            public void changeToActive() {
                alertBootstrapService.start();
            }

            @Override
            public void changeToStandBy() {
                close();
            }
        });

        alertHAServer.start();

        log.info("AlertServer is started ...");
    }

    @PreDestroy
    public void close() {
        String cause = "AlertServer destroy";
        try {
            if (!stopped.compareAndSet(false, true)) {
                log.warn("AlertServer is already stopped");
                return;
            }
            if (!standalone) {
                ServerLifeCycleManager.toStopped();
            }
            log.info("AlertServer is stopping, cause: {}", cause);
            try (
                    final AlertRpcServer ignore = alertRpcServer;
                    final AlertRegistryClient ignore1 = alertRegistryClient;
                    final AlertHAServer ignore2 = alertHAServer;
                    final AlertBootstrapService ignore3 = alertBootstrapService;) {
            }
            // thread sleep 3 seconds for thread quietly stop
            ThreadUtils.sleep(Constants.SERVER_CLOSE_WAIT_TIME.toMillis());
            log.info("AlertServer stopped, cause: {}", cause);
        } catch (Exception e) {
            log.error("AlertServer stop failed, cause: {}", cause, e);
        }
    }

}
