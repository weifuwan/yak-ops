package io.yak.ops.plugin.task.api;

/**
 * Task 插件加载或参数校验失败，不包含原始任务参数和敏感连接信息。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public class TaskPluginException extends RuntimeException {

    public TaskPluginException(String message) {
        super(message);
    }

    public TaskPluginException(String message, Throwable cause) {
        super(message, cause);
    }
}
