package io.yak.ops.plugin.alert.script;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ProcessUtils {

    private ProcessUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * executeScript
     *
     * @param cmd cmd params
     * @return exit code
     */
    static Integer executeScript(String... cmd) {

        int exitCode = -1;

        ProcessBuilder processBuilder = new ProcessBuilder(cmd);
        try {
            Process process = processBuilder.start();
            StreamGobbler inputStreamGobbler = new StreamGobbler(process.getInputStream());
            StreamGobbler errorStreamGobbler = new StreamGobbler(process.getErrorStream());

            inputStreamGobbler.start();
            errorStreamGobbler.start();
            return process.waitFor();
        } catch (IOException | InterruptedException e) {
            log.error("execute alert script error {}", e.getMessage());
            Thread.currentThread().interrupt();
        }

        return exitCode;
    }
}
