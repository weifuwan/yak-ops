package io.yak.ops.plugin.task.remoteshell;

import static io.yak.ops.plugin.task.api.TaskConstants.EXIT_CODE_FAILURE;

import io.yak.ops.common.utils.FileUtils;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.datasource.api.utils.DataSourceUtils;
import io.yak.ops.plugin.datasource.ssh.param.SSHConnectionParam;
import io.yak.ops.plugin.task.api.AbstractTask;
import io.yak.ops.plugin.task.api.TaskCallBack;
import io.yak.ops.plugin.task.api.TaskException;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.enums.ResourceType;
import io.yak.ops.plugin.task.api.model.Property;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;
import io.yak.ops.plugin.task.api.parameters.resource.DataSourceParameters;
import io.yak.ops.plugin.task.api.utils.ParameterUtils;
import io.yak.ops.spi.enums.DbType;

import org.apache.commons.lang3.SystemUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RemoteShellTask extends AbstractTask {

    static final String TASK_ID_PREFIX = "yak-ops-remoteshell-";

    /**
     * shell parameters
     */
    private RemoteShellParameters remoteShellParameters;

    private TaskExecutionContext taskExecutionContext;

    private RemoteExecutor remoteExecutor;

    private String taskId;

    /**
     * constructor
     *
     * @param taskExecutionContext taskExecutionContext
     */
    public RemoteShellTask(TaskExecutionContext taskExecutionContext) {
        super(taskExecutionContext);

        this.taskExecutionContext = taskExecutionContext;
    }

    @Override
    public void init() {
        log.info("shell task params {}", taskExecutionContext.getTaskParams());

        remoteShellParameters =
                JSONUtils.parseObject(taskExecutionContext.getTaskParams(), RemoteShellParameters.class);

        if (!remoteShellParameters.checkParameters()) {
            throw new TaskException("sell task params is not valid");
        }

        taskId = taskExecutionContext.getAppIds();
        if (taskId == null) {
            taskId = TASK_ID_PREFIX + taskExecutionContext.getTaskInstanceId();
        }
        setAppIds(taskId);
        taskExecutionContext.setAppIds(taskId);

        initRemoteExecutor();
    }

    @Override
    public void handle(TaskCallBack taskCallBack) throws TaskException {
        // add task close method to release resource
        try (RemoteExecutor executor = remoteExecutor) {
            // construct process
            String localFile = buildCommand();
            int exitCode = remoteExecutor.run(taskId, localFile);
            setExitStatusCode(exitCode);
            remoteShellParameters.dealOutParam(remoteExecutor.getTaskOutputParams());
        } catch (Exception e) {
            log.error("shell task error", e);
            setExitStatusCode(EXIT_CODE_FAILURE);
            throw new TaskException("Execute shell task error", e);
        }
    }

    @Override
    public void cancel() throws TaskException {
        // cancel process
        try {
            log.info("kill remote task {}", taskId);
            remoteExecutor.kill(taskId);
        } catch (Exception e) {
            throw new TaskException("cancel application error", e);
        }
    }

    /**
     * create command
     *
     * @return file name
     * @throws Exception exception
     */
    public String buildCommand() throws Exception {
        // generate scripts
        String fileName = String.format("%s/%s_node.%s",
                taskExecutionContext.getExecutePath(),
                taskExecutionContext.getTaskAppId(), SystemUtils.IS_OS_WINDOWS ? "bat" : "sh");

        File file = new File(fileName);
        Path path = file.toPath();

        if (Files.exists(path)) {
            // this shouldn't happen
            log.warn("The command file: {} is already exist", path);
            return fileName;
        }

        String script = remoteShellParameters.getRawScript().replaceAll("\\r\\n", "\n");
        script = parseScript(script);

        String environment = taskExecutionContext.getEnvironmentConfig();
        if (environment != null) {
            environment = environment.replaceAll("\\r\\n", "\n");
            environment = environment.replace("\r\n", "\n");
            script = environment + "\n" + script;
        }
        script = String.format(RemoteExecutor.COMMAND.HEADER) + script;
        script += String.format(RemoteExecutor.COMMAND.ADD_STATUS_COMMAND, RemoteExecutor.STATUS_TAG_MESSAGE);

        FileUtils.createFileWith755(path);
        Files.write(path, script.getBytes(), StandardOpenOption.APPEND);
        log.info("raw script : {}", script);
        return fileName;
    }

    @Override
    public AbstractParameters getParameters() {
        return remoteShellParameters;
    }

    private String parseScript(String script) {
        Map<String, Property> paramsMap = taskExecutionContext.getPrepareParamsMap();
        return ParameterUtils.convertParameterPlaceholders(script, ParameterUtils.convert(paramsMap));
    }

    public void initRemoteExecutor() {
        DataSourceParameters dbSource = (DataSourceParameters) taskExecutionContext.getResourceParametersHelper()
                .getResourceParameters(ResourceType.DATASOURCE, remoteShellParameters.getDatasource());
        taskExecutionContext.getResourceParametersHelper().getResourceParameters(ResourceType.DATASOURCE,
                remoteShellParameters.getDatasource());
        SSHConnectionParam sshConnectionParam = (SSHConnectionParam) DataSourceUtils.buildConnectionParams(
                DbType.valueOf(remoteShellParameters.getType()),
                dbSource.getConnectionParams());
        remoteExecutor = new RemoteExecutor(sshConnectionParam);
    }
}
