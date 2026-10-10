package io.yak.ops.plugin.task.datasync;

import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.plugin.task.api.TaskParameters;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginException;

/**
 * DATA_SYNC 任务参数解析入口；不创建执行实例或提交 YakFlow Job。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public final class SyncPlugin implements TaskPlugin {

    public static final String TYPE = "DATA_SYNC";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public TaskParameters parseParameters(String parametersJson) {
        try {
            SyncParameters parameters = JSONUtils.parseObject(parametersJson, SyncParameters.class);
            if (parameters == null) {
                throw new TaskPluginException("DATA_SYNC parameters must be a JSON object");
            }
            return parameters;
        } catch (IllegalArgumentException exception) {
            throw new TaskPluginException("Invalid DATA_SYNC parameters", exception);
        }
    }
}
