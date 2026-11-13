package io.yak.ops.api;

import io.yak.ops.api.metrics.ApiServerMetrics;
import io.yak.ops.common.CommonConfiguration;
import io.yak.ops.common.lifecycle.ServerLifeCycleManager;
import io.yak.ops.common.thread.DefaultUncaughtExceptionHandler;
import io.yak.ops.dao.DaoConfiguration;
import io.yak.ops.modeling.config.ModelingConfiguration;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginManager;
import io.yak.ops.plugin.storage.api.StorageConfiguration;
import io.yak.ops.plugin.task.api.TaskPluginManager;
import io.yak.ops.registry.api.RegistryConfiguration;
import io.yak.ops.service.ServiceConfiguration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Import({DaoConfiguration.class,
        CommonConfiguration.class,
        ServiceConfiguration.class,
        StorageConfiguration.class,
        RegistryConfiguration.class,
        ModelingConfiguration.class})
@ServletComponentScan
@SpringBootApplication
public class ApiApplicationServer {

    public static void main(String[] args) {
        ApiServerMetrics.registerUncachedException(DefaultUncaughtExceptionHandler::getUncaughtExceptionCount);
        Thread.setDefaultUncaughtExceptionHandler(DefaultUncaughtExceptionHandler.getInstance());
        SpringApplication.run(ApiApplicationServer.class);
    }

    @EventListener
    public void run(ApplicationReadyEvent readyEvent) {
        ServerLifeCycleManager.toRunning();
        log.info("Received spring application context ready event will load taskPlugin and write to DB");
        DataSourcePluginManager.loadDataSourcePlugin();
        TaskPluginManager.loadTaskPlugin();
    }
}
