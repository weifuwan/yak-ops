package io.yak.ops.plugin.task.api.shell;

import java.io.File;
import java.io.IOException;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BaseShellInterceptor implements IShellInterceptor {

    protected final String workingDirectory;
    protected final List<String> executeCommands;

    protected BaseShellInterceptor(List<String> executeCommands, String workingDirectory) {
        this.executeCommands = executeCommands;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public Process execute() throws IOException {
        // init process builder
        ProcessBuilder processBuilder = new ProcessBuilder();
        // setting up a working directory
        processBuilder.directory(new File(workingDirectory));
        // merge error information to standard output stream
        processBuilder.redirectErrorStream(true);
        processBuilder.command(executeCommands);
        log.info("Executing shell command : {}", String.join(" ", executeCommands));
        return processBuilder.start();
    }

}
