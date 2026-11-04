package io.yak.ops.plugin.alert.script;

import io.yak.ops.alert.api.AlertResult;
import io.yak.ops.common.utils.OSUtils;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ScriptSender {

    private static final String ALERT_TITLE_OPTION = "-t";
    private static final String ALERT_CONTENT_OPTION = "-c";
    private static final String ALERT_USER_PARAMS_OPTION = "-p";
    private final String scriptPath;
    private final String scriptType;
    private final String userParams;

    ScriptSender(Map<String, String> config) {
        scriptPath = StringUtils.isNotBlank(config.get(ScriptParamsConstants.NAME_SCRIPT_PATH))
                ? config.get(ScriptParamsConstants.NAME_SCRIPT_PATH)
                : "";
        scriptType = StringUtils.isNotBlank(config.get(ScriptParamsConstants.NAME_SCRIPT_TYPE))
                ? config.get(ScriptParamsConstants.NAME_SCRIPT_TYPE)
                : "";
        userParams = StringUtils.isNotBlank(config.get(ScriptParamsConstants.NAME_SCRIPT_USER_PARAMS))
                ? config.get(ScriptParamsConstants.NAME_SCRIPT_USER_PARAMS)
                : "";
    }

    AlertResult sendScriptAlert(String title, String content) {
        AlertResult alertResult = new AlertResult();
        if (ScriptType.SHELL.getDescp().equals(scriptType)) {
            return executeShellScript(title, content);
        }
        // If it is another type of alarm script can be added here, such as python

        alertResult.setSuccess(false);
        log.error("script type error: {}", scriptType);
        alertResult.setMessage("script type error : " + scriptType);
        return alertResult;
    }

    private AlertResult executeShellScript(String title, String content) {
        AlertResult alertResult = new AlertResult();
        alertResult.setSuccess(false);
        if (Boolean.TRUE.equals(OSUtils.isWindows())) {
            alertResult.setMessage("shell script not support windows os");
            return alertResult;
        }
        if (!scriptPath.endsWith(".sh")) {
            alertResult.setMessage("shell script is invalid, only support .sh file");
            return alertResult;
        }

        // validate script path
        File shellScriptFile = new File(scriptPath);
        // validate existence
        if (!shellScriptFile.exists()) {
            log.error("shell script not exist : {}", scriptPath);
            alertResult.setMessage("shell script not exist : " + scriptPath);
            return alertResult;
        }
        // validate is file
        if (!shellScriptFile.isFile()) {
            log.error("shell script is not a file : {}", scriptPath);
            alertResult.setMessage("shell script is not a file : " + scriptPath);
            return alertResult;
        }

        String[] cmd = {scriptPath, ALERT_TITLE_OPTION, title, ALERT_CONTENT_OPTION, content, ALERT_USER_PARAMS_OPTION,
                userParams};
        int exitCode = ProcessUtils.executeScript(cmd);

        if (exitCode == 0) {
            alertResult.setSuccess(true);
            alertResult.setMessage("send script alert msg success");
            return alertResult;
        }
        alertResult.setMessage("send script alert msg error,exitCode is " + exitCode);
        log.info("send script alert msg error,exitCode is {}", exitCode);
        return alertResult;
    }

}
