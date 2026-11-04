package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.Tenant;

import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface TenantMapper extends BaseMapper<Tenant> {

    /**
     * query tenant by id
     *
     * @param tenantId tenantId
     * @return tenant
     */
    Tenant queryById(@Param("tenantId") int tenantId);

    /**
     * delete by id
     */
    int deleteById(int id);

    /**
     * update
     */
    int updateById(@Param("et") Tenant tenant);

    /**
     * query tenant by code
     *
     * @param tenantCode tenantCode
     * @return tenant
     */
    Tenant queryByTenantCode(@Param("tenantCode") String tenantCode);

    /**
     * query tenants by queue id
     *
     * @param queueId queue id
     * @return tenant list
     */
    List<Tenant> queryTenantListByQueueId(@Param("queueId") Integer queueId);
    /**
     * tenant page
     *
     * @param page page
     * @param searchVal searchVal
     * @return tenant IPage
     */
    IPage<Tenant> queryTenantPaging(IPage<Tenant> page, @Param("ids") List<Integer> ids,
                                    @Param("searchVal") String searchVal);

    /**
     * check tenant exist
     *
     * @param tenantCode tenantCode
     * @return true if exist else return null
     */
    Boolean existTenant(@Param("tenantCode") String tenantCode);

    /**
     * queryTenantPagingByIds
     * @param page
     * @param ids
     * @param searchVal
     * @return
     */
    IPage<Tenant> queryTenantPagingByIds(Page<Tenant> page, @Param("ids") List<Integer> ids,
                                         @Param("searchVal") String searchVal);

    /**
     * queryAll
     * @return
     */
    List<Tenant> queryAll();
}
