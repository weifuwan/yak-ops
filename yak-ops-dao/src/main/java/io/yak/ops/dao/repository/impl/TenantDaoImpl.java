package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.Tenant;
import io.yak.ops.dao.mapper.TenantMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.TenantDao;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.NonNull;

@Repository
public class TenantDaoImpl extends BaseDao<Tenant, TenantMapper> implements TenantDao {

    public TenantDaoImpl(@NonNull TenantMapper tenantMapper) {
        super(tenantMapper);
    }

    @Override
    public Optional<Tenant> queryByCode(String tenantCode) {
        return Optional.ofNullable(mybatisMapper.queryByTenantCode(tenantCode));
    }

    @Override
    public Tenant queryDetailById(int tenantId) {
        return mybatisMapper.queryById(tenantId);
    }

    @Override
    public List<Tenant> queryTenantListByQueueId(Integer queueId) {
        return mybatisMapper.queryTenantListByQueueId(queueId);
    }

    @Override
    public IPage<Tenant> queryTenantPaging(IPage<Tenant> page, List<Integer> ids, String searchVal) {
        return mybatisMapper.queryTenantPaging(page, ids, searchVal);
    }

    @Override
    public boolean existTenant(String tenantCode) {
        return Boolean.TRUE.equals(mybatisMapper.existTenant(tenantCode));
    }
}
