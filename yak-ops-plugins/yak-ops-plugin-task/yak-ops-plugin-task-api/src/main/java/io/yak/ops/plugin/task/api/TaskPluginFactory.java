package io.yak.ops.plugin.task.api;

/**
 * Task 插件的 ServiceLoader 发现入口，注册稳定类型并创建对应插件。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public interface TaskPluginFactory {

    /** 返回开放集合中的稳定任务类型，例如 DATA_SYNC。 */
    String type();

    /** 创建只负责参数处理的插件，不得在此启动业务 Task。 */
    TaskPlugin create();
}
