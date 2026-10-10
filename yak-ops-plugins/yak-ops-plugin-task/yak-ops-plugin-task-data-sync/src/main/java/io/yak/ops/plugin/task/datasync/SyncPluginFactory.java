package io.yak.ops.plugin.task.datasync;

import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginFactory;

/**
 * 通过 Java ServiceLoader 注册 DATA_SYNC 任务类型。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public final class SyncPluginFactory implements TaskPluginFactory {

    @Override
    public String type() {
        return SyncPlugin.TYPE;
    }

    @Override
    public TaskPlugin create() {
        return new SyncPlugin();
    }
}
