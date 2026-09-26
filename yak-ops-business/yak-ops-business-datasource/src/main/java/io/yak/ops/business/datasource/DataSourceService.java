package io.yak.ops.business.datasource;

import io.yak.ops.common.bean.dto.datasource.DataSourceBatchIdsDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceConnectTestDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceQueryDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceBatchConnectTestResultVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceConnectionPropertyKeysVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.page.PagingData;
import java.util.List;

/**
 * Datasource 管理对 Boot 暴露的唯一稳定 Service Contract。
 *
 * <p>负责数据源增删改查、分页查询和连接测试；数据库差异和连接参数解析通过内部 Plugin 能力完成。</p>
 *
 * @author weifuwan
 * @since 2026-09-25
 */
public interface DataSourceService {

    /**
     * 创建数据源并持久化规范化后的连接参数。
     *
     * @param dto 数据源配置
     * @return 创建成功返回 true
     */
    boolean addDataSource(DataSourceDTO dto);

    /**
     * 更新已有数据源；当前不允许通过编辑操作修改数据源类型。
     *
     * @param id 数据源 ID
     * @param dto 更新后的数据源配置
     * @return 更新成功返回 true
     */
    boolean updateDataSource(String id, DataSourceDTO dto);

    /** 查询数据源详情，返回的敏感连接信息必须经过遮罩。 */
    DataSourceVO queryDataSource(String id);

    /**
     * 查询指定 Provider 可推荐的高级连接参数名。
     *
     * @param dbType 数据源类型或兼容别名
     * @return Provider 属性名候选项
     */
    DataSourceConnectionPropertyKeysVO queryConnectionPropertyKeys(String dbType);

    /** 删除指定数据源。 */
    boolean deleteDataSource(String id);

    /**
     * 在同一事务中批量删除数据源；任意数据源不存在或删除失败时整体回滚。
     *
     * @param dto 待删除的数据源 ID
     * @return 全部删除成功返回 true
     */
    boolean batchDeleteDataSources(DataSourceBatchIdsDTO dto);

    /** 按当前分页 Contract 查询数据源列表。 */
    PagingData<DataSourceVO> queryDataSourcePage(DataSourceQueryDTO dto);

    /**
     * 测试已保存数据源连接，并同步更新其连接状态。
     *
     * @param id 数据源 ID
     * @return 连接成功返回 true
     */
    boolean testConnection(String id);

    /**
     * 批量测试已保存数据源连接；单个连接失败不会中断其他数据源测试。
     *
     * @param dto 待测试的数据源 ID
     * @return 按请求顺序返回每个数据源的连接结果
     */
    List<DataSourceBatchConnectTestResultVO> batchTestConnections(DataSourceBatchIdsDTO dto);

    /**
     * 使用请求参数执行一次连接测试，不持久化新的数据源配置。
     *
     * @param dto 连接测试参数
     * @return 连接成功返回 true
     */
    boolean testConnection(DataSourceConnectTestDTO dto);
}
