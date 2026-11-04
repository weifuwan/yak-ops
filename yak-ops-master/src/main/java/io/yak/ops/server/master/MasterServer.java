package io.yak.ops.server.master;

import io.yak.ops.common.CommonConfiguration;
import io.yak.ops.common.IStoppable;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.lifecycle.ServerLifeCycleManager;
import io.yak.ops.common.thread.DefaultUncaughtExceptionHandler;
import io.yak.ops.common.thread.ThreadUtils;
import io.yak.ops.dao.DaoConfiguration;
import io.yak.ops.meter.metrics.MetricsProvider;
import io.yak.ops.meter.metrics.SystemMetrics;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginManager;
import io.yak.ops.plugin.storage.api.StorageConfiguration;
import io.yak.ops.plugin.task.api.TaskPluginManager;
import io.yak.ops.registry.api.RegistryConfiguration;
import io.yak.ops.scheduler.api.SchedulerApi;
import io.yak.ops.server.master.cluster.ClusterManager;
import io.yak.ops.server.master.cluster.ClusterStateMonitors;
import io.yak.ops.server.master.engine.MasterCoordinator;
import io.yak.ops.server.master.engine.WorkflowEngine;
import io.yak.ops.server.master.engine.system.SystemEventBus;
import io.yak.ops.server.master.engine.system.SystemEventBusFireWorker;
import io.yak.ops.server.master.engine.system.event.GlobalMasterFailoverEvent;
import io.yak.ops.server.master.engine.task.dispatcher.WorkerGroupDispatcherCoordinator;
import io.yak.ops.server.master.metrics.MasterServerMetrics;
import io.yak.ops.server.master.registry.MasterRegistryClient;
import io.yak.ops.server.master.rpc.MasterRpcServer;
import io.yak.ops.server.master.utils.MasterThreadFactory;
import io.yak.ops.service.ServiceConfiguration;
import io.yak.ops.service.bean.SpringApplicationContext;

import java.util.Date;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Import({DaoConfiguration.class,
        ServiceConfiguration.class,
        CommonConfiguration.class,
        StorageConfiguration.class,
        RegistryConfiguration.class})
@SpringBootApplication
public class MasterServer implements IStoppable {

    @Autowired
    private SpringApplicationContext springApplicationContext;

    @Autowired
    private MasterRegistryClient masterRegistryClient;

    @Autowired
    private WorkflowEngine workflowEngine;

    @Autowired
    private SchedulerApi schedulerApi;

    @Autowired
    private MasterRpcServer masterRPCServer;

    @Autowired
    private MetricsProvider metricsProvider;

    @Autowired
    private ClusterStateMonitors clusterStateMonitors;

    @Autowired
    private ClusterManager clusterManager;

    @Autowired
    private SystemEventBus systemEventBus;

    @Autowired
    private SystemEventBusFireWorker systemEventBusFireWorker;

    @Autowired
    private MasterCoordinator masterCoordinator;

    @Autowired
    private WorkerGroupDispatcherCoordinator workerGroupDispatcherCoordinator;

    public static void main(String[] args) {
        MasterServerMetrics.registerUncachedException(DefaultUncaughtExceptionHandler::getUncaughtExceptionCount);

        Thread.setDefaultUncaughtExceptionHandler(DefaultUncaughtExceptionHandler.getInstance());
        Thread.currentThread().setName(Constants.THREAD_NAME_MASTER_SERVER);
        SpringApplication.run(MasterServer.class);
    }

    /**
     * run master server
     */
    @PostConstruct
    public void initialized() {
        ServerLifeCycleManager.toRunning();

        // init rpc server
        this.masterRPCServer.start();

        // install task plugin
        TaskPluginManager.loadTaskPlugin();
        DataSourcePluginManager.loadDataSourcePlugin();

        // self tolerant
        this.masterRegistryClient.start();
        this.masterRegistryClient.setRegistryStoppable(this);

        this.masterCoordinator.start();

        this.clusterManager.start();

        this.clusterStateMonitors.start();

        this.workflowEngine.start();

        this.schedulerApi.start();

        this.systemEventBus
                .publish(GlobalMasterFailoverEvent.of(new Date(ServerLifeCycleManager.getServerStartupTime())));
        this.systemEventBusFireWorker.start();

        MasterServerMetrics.registerMasterCpuUsageGauge(() -> {
            SystemMetrics systemMetrics = metricsProvider.getSystemMetrics();
            return systemMetrics.getSystemCpuUsagePercentage();
        });
        MasterServerMetrics.registerMasterMemoryAvailableGauge(() -> {
            SystemMetrics systemMetrics = metricsProvider.getSystemMetrics();
            return (systemMetrics.getSystemMemoryMax() - systemMetrics.getSystemMemoryUsed()) / 1024.0 / 1024 / 1024;
        });
        MasterServerMetrics.registerMasterMemoryUsageGauge(() -> {
            SystemMetrics systemMetrics = metricsProvider.getSystemMetrics();
            return systemMetrics.getJvmMemoryUsedPercentage();
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!ServerLifeCycleManager.isStopped()) {
                close("MasterServer shutdownHook");
            }
        }));
        log.info("MasterServer initialized successfully in {} ms",
                System.currentTimeMillis() - ServerLifeCycleManager.getServerStartupTime());
    }

    @PreDestroy
    public void shutdown() {
        close("MasterServer shutdown");
    }

    public void close(String cause) {
        // set stop signal is true
        // execute only once
        if (!ServerLifeCycleManager.toStopped()) {
            log.warn("MasterServer is already stopped, current cause: {}", cause);
            return;
        }
        // thread sleep 3 seconds for thread quietly stop
        ThreadUtils.sleep(Constants.SERVER_CLOSE_WAIT_TIME.toMillis());
        MasterThreadFactory.getDefaultSchedulerThreadExecutor().shutdownNow();
        try (
                SystemEventBusFireWorker systemEventBusFireWorker1 = systemEventBusFireWorker;
                WorkflowEngine workflowEngine1 = workflowEngine;
                SchedulerApi closedSchedulerApi = schedulerApi;
                MasterRpcServer closedRpcServer = masterRPCServer;
                MasterCoordinator closeMasterCoordinator = masterCoordinator;
                MasterRegistryClient closedMasterRegistryClient = masterRegistryClient;
                // close spring Context and will invoke method with @PreDestroy annotation to destroy beans.
                // like ServerNodeManager,HostManager,TaskResponseService,CuratorZookeeperClient,etc
                SpringApplicationContext closedSpringContext = springApplicationContext;
                WorkerGroupDispatcherCoordinator closeWorkerGroupDispatcherCoordinator =
                        workerGroupDispatcherCoordinator) {

            log.info("MasterServer is stopping, current cause : {}", cause);
        } catch (Exception e) {
            log.error("MasterServer stop failed, current cause: {}", cause, e);
            return;
        }
        log.info("MasterServer stopped, current cause: {}", cause);
    }

    @Override
    public void stop(String cause) {
        close(cause);

        // make sure exit after server closed, don't call System.exit in close logic, will cause deadlock if close
        // multiple times at the same time
        System.exit(1);
    }
}
