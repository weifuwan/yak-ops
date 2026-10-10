package io.yak.ops.plugin.task.api;

/**
 * 插件专属的任务参数契约，负责纯参数校验，不读取 Workspace 或 Datasource。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public interface TaskParameters {

    /** 验证本地参数结构和基本语义，不解析连接凭证或查询数据库。 */
    void validate();
}
