package io.yak.ops.plugin.task.shell;

import static io.yak.ops.plugin.task.api.TaskConstants.EXIT_CODE_FAILURE;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.AbstractTask;
import io.yak.ops.plugin.task.api.ShellCommandExecutor;
import io.yak.ops.plugin.task.api.TaskCallBack;
import io.yak.ops.plugin.task.api.TaskException;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.model.TaskResponse;
import io.yak.ops.plugin.task.api.parameters.AbstractParameters;
import io.yak.ops.plugin.task.api.shell.IShellInterceptorBuilder;
import io.yak.ops.plugin.task.api.shell.ShellInterceptorBuilderFactory;
import io.yak.ops.plugin.task.api.utils.ParameterUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ShellTask extends AbstractTask {

    private ShellParameters shellParameters;

    private final ShellCommandExecutor shellCommandExecutor;

    public ShellTask(TaskExecutionContext taskExecutionContext) {
        super(taskExecutionContext);

        this.shellCommandExecutor = new ShellCommandExecutor(taskExecutionContext);
    }

    @Override
    public void init() {

        shellParameters = JSONUtils.parseObject(taskRequest.getTaskParams(), ShellParameters.class);
        log.info("Initialize shell task params {}", JSONUtils.toPrettyJsonString(shellParameters));

        if (shellParameters == null || !shellParameters.checkParameters()) {
            throw new TaskException("shell task params is not valid");
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handle(TaskCallBack taskCallBack) throws TaskException {
        try {
            IShellInterceptorBuilder<?, ?> shellActuatorBuilder = ShellInterceptorBuilderFactory.newBuilder()
                    .properties(ParameterUtils.convert(taskRequest.getPrepareParamsMap()))
                    .appendScript(shellParameters.getRawScript());

            TaskResponse commandExecuteResult = shellCommandExecutor.run(shellActuatorBuilder, taskCallBack);
            setExitStatusCode(commandExecuteResult.getExitStatusCode());
            setProcessId(commandExecuteResult.getProcessId());
            shellParameters.dealOutParam(shellCommandExecutor.getTaskOutputParams());
            taskRequest.setVarPool(shellParameters.getVarPool());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("The current Shell task has been interrupted", e);
            setExitStatusCode(EXIT_CODE_FAILURE);
            throw new TaskException("The current Shell task has been interrupted", e);
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
            shellCommandExecutor.cancelProcess();
        } catch (Exception e) {
            throw new TaskException("cancel application error", e);
        }
    }

    @Override
    public AbstractParameters getParameters() {
        return shellParameters;
    }

}
