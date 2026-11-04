package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.Tenant;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.metadata.IPage;

public interface TenantDao extends IDao<Tenant> {

    Optional<Tenant> queryByCode(String tenantCode);

    Tenant queryDetailById(int tenantId);

    List<Tenant> queryTenantListByQueueId(Integer queueId);

    IPage<Tenant> queryTenantPaging(IPage<Tenant> page, List<Integer> ids, String searchVal);

    boolean existTenant(String tenantCode);
}
