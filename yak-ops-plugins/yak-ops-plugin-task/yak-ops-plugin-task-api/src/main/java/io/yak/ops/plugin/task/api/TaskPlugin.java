package io.yak.ops.plugin.task.api;

/**
 * 任务类型插件，只负责解释自身配置，不管理任务定义、实例、调度或执行生命周期。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public interface TaskPlugin {

    /** 返回稳定的任务类型标识，必须与创建它的 Factory 一致。 */
    String type();

    /**
     * 将任务定义中的 JSON 配置解析为插件专属参数对象。
     *
     * <p>解析不访问数据库、持久化实例或启动运行时。参数约束由返回对象的 validate() 检查。
     *
     * @param parametersJson 任务定义中的插件配置，不包含工作空间身份或连接凭证
     * @return 可验证的插件专属参数
     */
    TaskParameters parseParameters(String parametersJson);
}
