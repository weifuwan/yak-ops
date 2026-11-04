package io.yak.ops.api.audit.operator.impl;

import io.yak.ops.api.audit.operator.BaseAuditOperator;
import io.yak.ops.dao.entity.Tenant;
import io.yak.ops.dao.repository.TenantDao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TenantAuditOperatorImpl extends BaseAuditOperator {

    @Autowired
    private TenantDao tenantDao;

    @Override
    public String getObjectNameFromIdentity(Object identity) {
        Long objId = toLong(identity);
        if (objId == -1) {
            return "";
        }

        Tenant obj = tenantDao.queryById(objId);
        return obj == null ? "" : obj.getTenantCode();
    }
}
