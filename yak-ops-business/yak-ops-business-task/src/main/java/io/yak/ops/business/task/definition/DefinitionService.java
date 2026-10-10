package io.yak.ops.business.task.definition;

import io.yak.ops.common.bean.vo.task.DefinitionVO;
import io.yak.ops.common.bean.vo.task.DefinitionVersionVO;
import java.util.List;

/**
 * 通用 Task Definition 查询与可执行版本历史的稳定业务接口。
 *
 * <p>DATA_SYNC 的当前单表创建/修改仍由其业务 Service 进行物理 Schema 校验，
 * 当前实例与调度的迁移不属于此接口。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public interface DefinitionService {

    /** 查询当前 Workspace 中一个插件无关的任务定义。 */
    DefinitionVO query(String id);

    /** 列出当前 Workspace 中真实留存的定义版本，不虚构丢失的历史。 */
    List<DefinitionVersionVO> queryVersions(String id);
}
